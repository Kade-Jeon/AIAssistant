package com.kade.AIAssistant.infra.redis.context;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private static final TypeReference<List<MessageDto>> MESSAGE_LIST_TYPE = new TypeReference<>() {
    };

    private final PromptCacheService cache;
    private final ChatMemoryRepository repository;
    private final ObjectMapper objectMapper;

    @Override
    public void add(String conversationId, List<Message> messages) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        Assert.notNull(messages, "messages cannot be null");
        Assert.noNullElements(messages, "messages cannot contain null elements");

        // 저장 시 USER 메시지 중 파일 첨부 형식("다음 첨부파일(문서) 내용:...사용자 요청: X")은 사용자 요청(X)만 저장
        List<Message> toStore = messages.stream()
                .map(RedisChatMemory::toStoredMessage)
                .toList();

        List<Message> current = get(conversationId);
        List<Message> combined = new ArrayList<>(current);
        combined.addAll(toStore);
        repository.saveAll(conversationId, combined);
        writeCache(conversationId, combined);
        log.debug("[RedisChatMemory] add 완료 - conversationId: {}, 추가: {}, 전체: {}",
                conversationId, messages.size(), combined.size());
    }

    /**
     * 저장용 메시지로 변환. 파일 첨부 USER 메시지는 "사용자 요청:" 이후만 저장한다.
     * AI 호출 시에는 Controller에서 파일 본문+사용자 요청 전체를 보내므로, 저장 단계에서만 trim.
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
        writeCache(conversationId, fromDb);
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
     */
    public void warmCache(String conversationId, List<Message> messages) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        Assert.notNull(messages, "messages cannot be null");
        writeCache(conversationId, messages);
        log.debug("[RedisChatMemory] warmCache 완료 - conversationId: {}, 메시지 수: {}", conversationId, messages.size());
    }

    private String cacheKey(String conversationId) {
        return String.format("%s:%s", RedisKeyPrefix.CONTEXT, conversationId);
    }


    private void writeCache(String conversationId, List<Message> messages) {
        String cacheKey = cacheKey(conversationId);
        String json = toJson(messages);
        cache.set(cacheKey, json, CACHE_TTL);
    }

    private String toJson(List<Message> messages) {
        try {
            List<MessageDto> dtos = messages.stream()
                    .map(m -> new MessageDto(m.getMessageType().getValue(), m.getText()))
                    .toList();
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
            return dtos.stream().map(this::toMessage).toList();
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

    private record MessageDto(String messageType, String text) {
    }
}
