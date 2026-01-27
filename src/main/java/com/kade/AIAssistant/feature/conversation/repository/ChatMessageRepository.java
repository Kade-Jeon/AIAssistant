package com.kade.AIAssistant.feature.conversation.repository;

import com.kade.AIAssistant.feature.conversation.entity.ChatMessageEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * SPRING_AI_CHAT_MEMORY 조회용 JPA Repository.
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, UUID> {

    List<ChatMessageEntity> findByConversationIdOrderByTimestampDesc(String conversationId, Pageable pageable);

    default List<ChatMessageEntity> findRecentByConversationId(String conversationId, Pageable pageable) {
        return findByConversationIdOrderByTimestampDesc(conversationId, pageable);
    }

    /**
     * conversationId와 content 일부로 USER 타입 메시지를 찾습니다.
     * 파일 첨부 메시지의 경우 저장 시 "사용자 요청:" 이후 부분만 저장되므로, 이를 기준으로 찾습니다.
     */
    @Query("SELECT m FROM ChatMessageEntity m " +
           "WHERE m.conversationId = :conversationId " +
           "AND m.type = 'USER' " +
           "AND m.content LIKE :contentSnippet " +
           "ORDER BY m.timestamp DESC")
    Optional<ChatMessageEntity> findUserMessageByConversationIdAndContentSnippet(
            @Param("conversationId") String conversationId,
            @Param("contentSnippet") String contentSnippet
    );

    void deleteByConversationId(String conversationId);
}
