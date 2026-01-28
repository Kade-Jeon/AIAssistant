package com.kade.AIAssistant.infra.redis.context;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kade.AIAssistant.feature.conversation.entity.ChatMessageEntity;
import com.kade.AIAssistant.feature.conversation.repository.ChatMessageRepository;
import com.kade.AIAssistant.infra.redis.enums.RedisKeyPrefix;
import com.kade.AIAssistant.infra.redis.prompt.PromptCacheService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Cache-Aside 패턴 ChatMemory 구현.
 * <p>get: Redis 조회 → miss 시 RDB(ChatMemoryRepository) 조회 → Redis 캐싱 후 반환.
 * add: 기존 로드 후 append → RDB 저장 → Redis 갱신. clear: RDB 삭제 + Redis 삭제.
 * <p>
 * <b>보안:</b> conversationId는 "userId:sessionId" 또는 "userId:uuid" 형식 권장.
 * userId 검증은 상위 레이어(Controller/Service)에서 수행해야 함.
 */
@Slf4j
@RequiredArgsConstructor
public class RedisChatMemory implements ChatMemory {

    private static final Duration CACHE_TTL = Duration.ofHours(1L);
    private static final int MAX_CACHED_MESSAGES = 20; // 캐시에 저장할 최대 메시지 수 (최신 N개만 유지)
    private static final TypeReference<List<MessageDto>> MESSAGE_LIST_TYPE = new TypeReference<List<MessageDto>>() {
    };

    private final PromptCacheService cache;
    private final ChatMemoryRepository repository;
    private final ObjectMapper objectMapper;
    private final ChatMessageRepository chatMessageRepository;

    @Override
    public void add(String conversationId, List<Message> messages) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        Assert.notNull(messages, "messages cannot be null");
        Assert.noNullElements(messages, "messages cannot contain null elements");

        if (messages.isEmpty()) {
            log.debug("[RedisChatMemory] 추가할 메시지가 없습니다 - conversationId: {}", conversationId);
            return;
        }

        // MessageChatMemoryAdvisor가 호출하는 add()는 저장하지 않고 캐시만 갱신
        // 실제 저장은 ConversationService에서 직접 처리 (message_id 제어를 위해)
        // 저장 시 USER 메시지 중 파일 첨부 형식("다음 첨부파일(문서) 내용:...사용자 요청: X")은 사용자 요청(X)만 저장
        List<Message> toStore = messages.stream()
                .map(RedisChatMemory::toStoredMessage)
                .toList();

        // DB 저장은 하지 않음 (ConversationService에서 직접 저장)
        // Redis 캐시만 갱신: 기존 캐시 + 새 메시지 (최신 N개만 유지)
        List<Message> current = get(conversationId);
        List<Message> merged = mergeMessages(current, toStore);

        // 최신 N개만 유지 (메모리 효율을 위해)
        List<Message> limited = limitToRecent(merged, MAX_CACHED_MESSAGES);
        writeCache(conversationId, limited);

        log.debug("[RedisChatMemory] add 완료 (캐시만 갱신, DB 저장 안함) - conversationId: {}, 추가: {}, 병합 후: {}, 제한 후: {}",
                conversationId, toStore.size(), merged.size(), limited.size());
    }

    /**
     * 저장용 메시지로 변환. 파일 첨부 USER 메시지는 "사용자 요청:" 이후만 저장한다. AI 호출 시에는 Controller에서 파일 본문+사용자 요청 전체를 보내므로, 저장 단계에서만 trim.
     */
    private static Message toStoredMessage(Message m) {
        if (!(m instanceof UserMessage user)) {
            return m;
        }
        String text = user.getText();
        if (!StringUtils.hasText(text)) {
            return m;
        }
        String stored = extractUserRequestFromFileAttachment(text);
        if (stored == text) {
            return m;
        }
        return new UserMessage(stored);
    }

    private static final String FILE_ATTACHMENT_MARKER = "다음 첨부파일(문서) 내용:";
    private static final String USER_REQUEST_MARKER = "사용자 요청:";

    private static String extractUserRequestFromFileAttachment(String content) {
        if (!content.startsWith(FILE_ATTACHMENT_MARKER) || !content.contains(USER_REQUEST_MARKER)) {
            return content;
        }
        int idx = content.indexOf(USER_REQUEST_MARKER);
        String userRequest = content.substring(idx + USER_REQUEST_MARKER.length()).trim();
        return StringUtils.hasText(userRequest) ? userRequest : content;
    }

    @Override
    public List<Message> get(String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");

        String cacheKey = cacheKey(conversationId);
        Optional<Object> cached = cache.get(cacheKey);

        if (cached.isPresent() && cached.get() instanceof String json) {
            log.debug("[RedisChatMemory] Redis 캐시 히트: {}", conversationId);
            return fromJson(json);
        }

        log.debug("[RedisChatMemory] Redis 캐시 미스, RDB 조회: {}", conversationId);
        List<Message> fromDb = repository.findByConversationId(conversationId);
        // DB에서 조회한 메시지는 실제 timestamp 정보를 사용하여 캐시에 저장
        writeCacheWithTimestamp(conversationId, fromDb);
        return fromDb;
    }

    @Override
    public void clear(String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        repository.deleteByConversationId(conversationId);
        cache.delete(cacheKey(conversationId));
        log.debug("[RedisChatMemory] clear 완료: {}", conversationId);
    }

    /**
     * 조회된 대화 목록으로 Redis 캐시를 갱신한다. getConversation 등에서 DB 조회 후 ChatMemory가 이를 활용할 수 있도록 호출.
     * <p>기존 캐시와 병합하여 중복을 제거하고 timestamp 기준으로 정렬합니다.
     */
    public void warmCache(String conversationId, List<Message> messages) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        Assert.notNull(messages, "messages cannot be null");

        // 기존 캐시 조회
        List<Message> cached = get(conversationId);

        // 병합 (중복 제거, timestamp 기준 정렬)
        List<Message> merged = mergeMessages(cached, messages);

        // Redis 캐시 갱신
        writeCache(conversationId, merged);

        log.debug("[RedisChatMemory] warmCache 완료 - conversationId: {}, 기존: {}, 새: {}, 병합 후: {}",
                conversationId, cached.size(), messages.size(), merged.size());
    }

    /**
     * 메시지 리스트를 병합합니다. 중복 제거 후 timestamp 기준으로 정렬합니다.
     * <p>중복 판단 기준: content + messageType
     */
    private List<Message> mergeMessages(List<Message> existing, List<Message> newMessages) {
        if (existing.isEmpty()) {
            return new ArrayList<>(newMessages);
        }
        if (newMessages.isEmpty()) {
            return new ArrayList<>(existing);
        }

        // content + messageType을 키로 사용하여 중복 제거
        // timestamp 정보를 함께 저장하여 정렬 가능하도록 함
        java.util.Map<String, MessageWithTimestamp> messageMap = new java.util.LinkedHashMap<>();
        java.time.Instant now = java.time.Instant.now();

        // 기존 메시지 추가 (역순 인덱스로 timestamp 추정)
        for (int i = 0; i < existing.size(); i++) {
            Message msg = existing.get(i);
            String key = createMessageKey(msg);
            // 기존 메시지는 더 오래된 것으로 간주 (역순 인덱스)
            java.time.Instant timestamp = now.minusSeconds(existing.size() - i);
            messageMap.putIfAbsent(key, new MessageWithTimestamp(msg, timestamp));
        }

        // 새 메시지 추가 (기존 것과 중복되지 않는 것만, 최신 timestamp)
        for (Message msg : newMessages) {
            String key = createMessageKey(msg);
            if (!messageMap.containsKey(key)) {
                // 새 메시지는 가장 최신으로 간주
                messageMap.put(key, new MessageWithTimestamp(msg, now));
            }
        }

        // timestamp 기준 내림차순 정렬 (최신 메시지가 앞에)
        return messageMap.values().stream()
                .sorted((a, b) -> b.timestamp.compareTo(a.timestamp))
                .map(mwt -> mwt.message)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Message와 timestamp를 함께 저장하는 내부 클래스
     */
    private static class MessageWithTimestamp {
        final Message message;
        final java.time.Instant timestamp;

        MessageWithTimestamp(Message message, java.time.Instant timestamp) {
            this.message = message;
            this.timestamp = timestamp;
        }
    }

    /**
     * Message의 고유 키 생성 (content + messageType)
     */
    private String createMessageKey(Message message) {
        return createMessageKey(message.getMessageType().getValue(), message.getText());
    }

    /**
     * 메시지 리스트를 최신 N개로 제한합니다.
     * <p>메모리 효율을 위해 캐시에는 최신 메시지만 유지합니다.
     *
     * @param messages 전체 메시지 리스트
     * @param limit    최대 개수
     * @return 최신 N개 메시지
     */
    private List<Message> limitToRecent(List<Message> messages, int limit) {
        if (messages.size() <= limit) {
            return messages;
        }
        // 최신 메시지가 앞에 오도록 유지 (DB에서 timestamp DESC로 조회하므로)
        return messages.subList(0, limit);
    }

    private String cacheKey(String conversationId) {
        return String.format("%s:%s", RedisKeyPrefix.CONTEXT, conversationId);
    }


    private void writeCache(String conversationId, List<Message> messages) {
        String cacheKey = cacheKey(conversationId);
        String json = toJson(messages);
        cache.set(cacheKey, json, CACHE_TTL);
    }

    /**
     * DB에서 조회한 메시지를 캐시에 저장 (실제 timestamp 사용)
     */
    private void writeCacheWithTimestamp(String conversationId, List<Message> messages) {
        if (messages.isEmpty()) {
            writeCache(conversationId, messages);
            return;
        }

        // DB에서 실제 timestamp 조회
        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(0, messages.size());
        List<ChatMessageEntity> entities =
                chatMessageRepository.findRecentByConversationId(conversationId, pageable);

        // Message와 timestamp를 매핑
        java.util.Map<String, java.time.Instant> timestampMap = new java.util.HashMap<>();
        for (ChatMessageEntity entity : entities) {
            String key = createMessageKey(entity.getType(), entity.getContent());
            timestampMap.put(key, entity.getTimestamp());
        }

        // timestamp 정보를 포함하여 JSON 생성
        String cacheKey = cacheKey(conversationId);
        String json = toJsonWithTimestamp(messages, timestampMap);
        cache.set(cacheKey, json, CACHE_TTL);
    }

    /**
     * Message의 고유 키 생성 (type + content)
     */
    private String createMessageKey(String type, String content) {
        return type.toUpperCase() + "::" + (content != null ? content : "");
    }

    private String toJson(List<Message> messages) {
        return toJsonWithTimestamp(messages, null);
    }

    /**
     * Message 리스트를 JSON으로 변환 (timestamp 정보 포함 가능)
     *
     * @param messages     메시지 리스트
     * @param timestampMap Message의 고유 키를 키로 하는 timestamp 맵 (null이면 추정값 사용)
     */
    private String toJsonWithTimestamp(List<Message> messages,
                                       java.util.Map<String, java.time.Instant> timestampMap) {
        try {
            java.time.Instant baseTime = java.time.Instant.now();
            List<MessageDto> dtos = new java.util.ArrayList<>();

            for (int i = 0; i < messages.size(); i++) {
                Message m = messages.get(i);
                String key = createMessageKey(m.getMessageType().getValue(), m.getText());

                java.time.Instant timestamp;
                if (timestampMap != null && timestampMap.containsKey(key)) {
                    // DB에서 조회한 실제 timestamp 사용
                    timestamp = timestampMap.get(key);
                } else {
                    // 추정 timestamp (최신 메시지가 앞에 있으므로 역순)
                    timestamp = baseTime.minusSeconds(i);
                }

                dtos.add(new MessageDto(m.getMessageType().getValue(), m.getText(), timestamp));
            }

            return objectMapper.writeValueAsString(dtos);
        } catch (Exception e) {
            log.warn("[RedisChatMemory] 직렬화 실패", e);
            throw new RuntimeException("채팅 메모리 직렬화 실패", e);
        }
    }

    private List<Message> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<MessageDto> dtos = objectMapper.readValue(json, MESSAGE_LIST_TYPE);
            // timestamp 기준 내림차순 정렬 (최신 메시지가 앞에)
            return dtos.stream()
                    .sorted((a, b) -> {
                        if (a.timestamp() == null && b.timestamp() == null) {
                            return 0;
                        }
                        if (a.timestamp() == null) {
                            return 1;
                        }
                        if (b.timestamp() == null) {
                            return -1;
                        }
                        return b.timestamp().compareTo(a.timestamp());
                    })
                    .map(this::toMessage)
                    .toList();
        } catch (Exception e) {
            log.warn("[RedisChatMemory] 역직렬화 실패: {}", json, e);
            return List.of();
        }
    }

    private Message toMessage(MessageDto dto) {
        MessageType type = MessageType.fromValue(dto.messageType());
        return switch (type) {
            case SYSTEM -> new SystemMessage(dto.text());
            case USER -> new UserMessage(dto.text());
            case ASSISTANT -> new AssistantMessage(dto.text());
            default -> new AssistantMessage(dto.text());
        };
    }

    /**
     * 페이징 조회 후 Redis 캐시에 병합합니다.
     * <p>스크롤 업 시 이전 메시지를 조회한 후 캐시에 병합합니다.
     *
     * @param conversationId  대화 ID
     * @param beforeTimestamp 이 시간 이전의 메시지 조회 (스크롤 업)
     * @param limit           조회 개수
     * @return 병합된 메시지 리스트
     */
    public List<Message> getWithPaging(String conversationId, java.time.Instant beforeTimestamp, int limit) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        Assert.notNull(beforeTimestamp, "beforeTimestamp cannot be null");

        log.debug("[RedisChatMemory] 페이징 조회 - conversationId: {}, beforeTimestamp: {}, limit: {}",
                conversationId, beforeTimestamp, limit);

        // CustomChatMemoryRepository를 통해 페이징 조회
        List<Message> pagedMessages = ((CustomChatMemoryRepository) repository)
                .findByConversationIdAndTimestampBefore(conversationId, beforeTimestamp, limit);

        if (pagedMessages.isEmpty()) {
            log.debug("[RedisChatMemory] 페이징 조회 결과 없음 - conversationId: {}", conversationId);
            return get(conversationId);
        }

        // 기존 캐시와 병합
        List<Message> cached = get(conversationId);
        List<Message> merged = mergeMessages(cached, pagedMessages);

        // Redis 캐시 갱신
        writeCache(conversationId, merged);

        log.debug("[RedisChatMemory] 페이징 조회 완료 - conversationId: {}, 조회: {}, 기존: {}, 병합 후: {}",
                conversationId, pagedMessages.size(), cached.size(), merged.size());

        return merged;
    }

    private record MessageDto(String messageType, String text, java.time.Instant timestamp) {
        // 하위 호환성을 위해 timestamp가 없는 경우를 위한 생성자
        MessageDto(String messageType, String text) {
            this(messageType, text, null);
        }
    }
}
