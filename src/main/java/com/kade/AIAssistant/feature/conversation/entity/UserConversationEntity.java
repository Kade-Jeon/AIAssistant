package com.kade.AIAssistant.feature.conversation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 유저–대화 소유 매핑. 어떤 유저가 어떤 conversationId를 사용하는지, 제목(subject)과 함께 저장.
 */
@Entity
@Table(name = "USER_CONVERSATION")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserConversationEntity {

    @EmbeddedId
    private UserConversationId id;

    @Column(name = "subject", nullable = false, columnDefinition = "TEXT")
    private String subject;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UserConversationEntity(String userId, String conversationId, String subject) {
        this.id = new UserConversationId(userId, conversationId);
        this.subject = subject != null ? subject : "(제목 없음)";
        this.createdAt = Instant.now();
    }
}
