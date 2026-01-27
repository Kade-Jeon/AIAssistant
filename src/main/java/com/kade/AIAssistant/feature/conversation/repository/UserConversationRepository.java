package com.kade.AIAssistant.feature.conversation.repository;

import com.kade.AIAssistant.feature.conversation.entity.UserConversationEntity;
import com.kade.AIAssistant.feature.conversation.entity.UserConversationId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 유저–대화 소유 매핑 조회/저장.
 */
public interface UserConversationRepository extends JpaRepository<UserConversationEntity, UserConversationId> {

    boolean existsById_UserIdAndId_ConversationId(String userId, String conversationId);

    Optional<UserConversationEntity> findById_UserIdAndId_ConversationId(String userId, String conversationId);

    List<UserConversationEntity> findById_UserIdOrderByUpdatedAtDesc(String userId, Pageable pageable);

    int deleteById_UserIdAndId_ConversationId(String userId, String conversationId);
}
