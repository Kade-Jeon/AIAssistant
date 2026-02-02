package com.kade.AIAssistant.feature.conversation.repository;

import com.kade.AIAssistant.common.enums.MessageType;
import com.kade.AIAssistant.feature.conversation.entity.ChatMessageEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * CHAT_MESSAGE 테이블 조회용 JPA Repository.
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, UUID> {

    /**
     * conversationId로 최신 메시지 조회 (페이징)
     */
    List<ChatMessageEntity> findByConversationIdOrderByTimestampDesc(String conversationId, Pageable pageable);

    /**
     * conversationId의 가장 최근 USER 타입 메시지 ID 조회 (Idempotency claim 시 사용)
     */
    Optional<ChatMessageEntity> findFirstByConversationIdAndTypeOrderByTimestampDesc(
            String conversationId, MessageType type);

    /**
     * conversationId로 최신 메시지 조회 (기본 메서드)
     */
    default List<ChatMessageEntity> findRecentByConversationId(String conversationId, Pageable pageable) {
        return findByConversationIdOrderByTimestampDesc(conversationId, pageable);
    }

    /**
     * conversationId와 timestamp 이전의 메시지 조회 (페이징 - 스크롤 업용)
     */
    @Query("SELECT m FROM ChatMessageEntity m " +
           "WHERE m.conversationId = :conversationId " +
           "AND m.timestamp < :beforeTimestamp " +
           "ORDER BY m.timestamp DESC")
    List<ChatMessageEntity> findByConversationIdAndTimestampBefore(
            @Param("conversationId") String conversationId,
            @Param("beforeTimestamp") java.time.Instant beforeTimestamp,
            Pageable pageable
    );

    /**
     * conversationId와 content 일부로 USER 타입 메시지를 찾습니다.
     * 파일 첨부 메시지의 경우 저장 시 "사용자 요청:" 이후 부분만 저장되므로, 이를 기준으로 찾습니다.
     * 
     * @deprecated Phase 4에서 message_id 직접 사용으로 변경되어 더 이상 사용하지 않음.
     *             첨부파일 저장 시 message_id를 직접 전달하므로 이 메서드는 필요 없음.
     */
    @Deprecated
    @Query("SELECT m FROM ChatMessageEntity m " +
           "WHERE m.conversationId = :conversationId " +
           "AND m.type = com.kade.AIAssistant.common.enums.MessageType.USER " +
           "AND m.content LIKE :contentSnippet " +
           "ORDER BY m.timestamp DESC")
    Optional<ChatMessageEntity> findUserMessageByConversationIdAndContentSnippet(
            @Param("conversationId") String conversationId,
            @Param("contentSnippet") String contentSnippet
    );

    void deleteByConversationId(String conversationId);

    /**
     * 모든 conversationId 조회 (중복 제거)
     */
    @Query("SELECT DISTINCT m.conversationId FROM ChatMessageEntity m")
    List<String> findAllConversationIds();
}
