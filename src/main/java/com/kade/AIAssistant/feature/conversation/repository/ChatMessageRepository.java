package com.kade.AIAssistant.feature.conversation.repository;

import com.kade.AIAssistant.feature.conversation.entity.ChatMessageEntity;
import com.kade.AIAssistant.feature.conversation.entity.ChatMessageId;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * SPRING_AI_CHAT_MEMORY 조회용 JPA Repository. 메서드명 유도 쿼리: id.conversationId 조건, id.timestamp DESC 정렬, Pageable로
 * LIMIT/OFFSET 적용.
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, ChatMessageId> {

    List<ChatMessageEntity> findById_ConversationIdOrderById_TimestampDesc(String conversationId, Pageable pageable);

    default List<ChatMessageEntity> findRecentByConversationId(String conversationId, Pageable pageable) {
        return findById_ConversationIdOrderById_TimestampDesc(conversationId, pageable);
    }

    int deleteById_ConversationId(String conversationId);
}
