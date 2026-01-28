package com.kade.AIAssistant.infra.redis.context;

import com.kade.AIAssistant.feature.conversation.entity.ChatMessageEntity;
import com.kade.AIAssistant.feature.conversation.repository.ChatMessageRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

/**
 * 커스텀 ChatMemoryRepository 구현.
 * <p>CHAT_MESSAGE 테이블을 사용하여 메시지를 저장/조회합니다.
 * <p>saveAll()은 전체 교체가 아닌 추가만 수행합니다 (기존 메시지 유지).
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class CustomChatMemoryRepository implements ChatMemoryRepository {

    private final ChatMessageRepository chatMessageRepository;
    @Value("${app.conversation.context-limit:20}")
    private int defaultMessageLimit; // Reids 캐시 미스 시 조회할 기본 메시지 수

    @Override
    public List<Message> findByConversationId(String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");

        // 최신 N개만 조회 (페이징)
        Pageable pageable = PageRequest.of(0, defaultMessageLimit);
        List<ChatMessageEntity> entities = chatMessageRepository
                .findRecentByConversationId(conversationId, pageable);

        return entities.stream()
                .map(this::toMessage)
                .toList();
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        Assert.notNull(messages, "messages cannot be null");
        Assert.noNullElements(messages, "messages cannot contain null elements");

        if (messages.isEmpty()) {
            log.debug("[CustomChatMemoryRepository] 저장할 메시지가 없습니다 - conversationId: {}", conversationId);
            return;
        }

        // 기존 메시지 조회 (중복 체크용)
        List<ChatMessageEntity> existing = chatMessageRepository
                .findRecentByConversationId(conversationId, PageRequest.of(0, 100));

        // 기존 메시지의 content + type을 Set으로 변환 (중복 체크용)
        java.util.Set<String> existingKeys = existing.stream()
                .map(e -> createEntityKey(e.getType(), e.getContent()))
                .collect(java.util.stream.Collectors.toSet());

        // Message를 ChatMessageEntity로 변환 (중복 제외)
        List<ChatMessageEntity> entities = new ArrayList<>();
        Instant now = Instant.now();

        for (Message message : messages) {
            String type = message.getMessageType().getValue().toUpperCase();
            String content = message.getText();
            String key = createEntityKey(type, content);

            // 중복 체크: 기존에 같은 type + content가 있으면 스킵
            if (existingKeys.contains(key)) {
                log.debug("[CustomChatMemoryRepository] 중복 메시지 스킵 - conversationId: {}, type: {}, content 길이: {}",
                        conversationId, type, content != null ? content.length() : 0);
                continue;
            }

            ChatMessageEntity entity = new ChatMessageEntity(
                    conversationId,
                    type,
                    content,
                    now // timestamp는 현재 시간 사용
            );
            entities.add(entity);
            existingKeys.add(key); // 같은 배치 내 중복 방지
        }

        if (entities.isEmpty()) {
            log.debug("[CustomChatMemoryRepository] 저장할 새 메시지가 없습니다 (모두 중복) - conversationId: {}", conversationId);
            return;
        }

        // JPA saveAll로 저장 (새 메시지만)
        // ChatMessageRepository는 JpaRepository를 상속하므로 saveAll() 사용 가능
        chatMessageRepository.saveAll(entities);

        log.debug("[CustomChatMemoryRepository] 메시지 저장 완료 - conversationId: {}, 저장된 메시지 수: {}",
                conversationId, entities.size());
    }

    /**
     * Entity의 고유 키 생성 (type + content)
     */
    private String createEntityKey(String type, String content) {
        return type + "::" + (content != null ? content : "");
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        chatMessageRepository.deleteByConversationId(conversationId);
        log.debug("[CustomChatMemoryRepository] 메시지 삭제 완료 - conversationId: {}", conversationId);
    }

    @Override
    public List<String> findConversationIds() {
        // 모든 conversationId 조회 (중복 제거)
        return chatMessageRepository.findAllConversationIds();
    }

    /**
     * conversationId와 timestamp 이전의 메시지 조회 (페이징 - 스크롤 업용)
     *
     * @param conversationId  대화 ID
     * @param beforeTimestamp 이 시간 이전의 메시지 조회
     * @param limit           조회 개수
     * @return 조회된 메시지 리스트
     */
    public List<Message> findByConversationIdAndTimestampBefore(
            String conversationId, Instant beforeTimestamp, int limit) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        Assert.notNull(beforeTimestamp, "beforeTimestamp cannot be null");

        Pageable pageable = PageRequest.of(0, limit);
        List<ChatMessageEntity> entities = chatMessageRepository
                .findByConversationIdAndTimestampBefore(conversationId, beforeTimestamp, pageable);

        return entities.stream()
                .map(this::toMessage)
                .toList();
    }

    /**
     * ChatMessageEntity를 Spring AI Message로 변환
     */
    private Message toMessage(ChatMessageEntity entity) {
        MessageType type = MessageType.fromValue(entity.getType().toLowerCase());
        String text = entity.getContent();

        return switch (type) {
            case SYSTEM -> new SystemMessage(text);
            case USER -> new UserMessage(text);
            case ASSISTANT -> new AssistantMessage(text);
            default -> new AssistantMessage(text);
        };
    }
}
