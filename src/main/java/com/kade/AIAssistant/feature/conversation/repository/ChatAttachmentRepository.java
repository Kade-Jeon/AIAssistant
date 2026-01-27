package com.kade.AIAssistant.feature.conversation.repository;

import com.kade.AIAssistant.feature.conversation.entity.ChatAttachmentEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * CHAT_ATTACHMENT 조회/저장용 JPA Repository.
 */
public interface ChatAttachmentRepository extends JpaRepository<ChatAttachmentEntity, Long> {

    List<ChatAttachmentEntity> findByConversationIdOrderByCreatedAtDesc(String conversationId);

    void deleteByConversationId(String conversationId);
}
