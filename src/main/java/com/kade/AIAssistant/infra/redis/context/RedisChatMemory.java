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

        List<Message> current = get(conversationId);
        List<Message> combined = new ArrayList<>(current);
        combined.addAll(messages);
        repository.saveAll(conversationId, combined);
        writeCache(conversationId, combined);
        log.debug("[RedisChatMemory] add 완료 - conversationId: {}, 추가: {}, 전체: {}",
                conversationId, messages.size(), combined.size());
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
