package com.kade.AIAssistant.feature.conversation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 유저–대화 소유 매핑. 어떤 유저가 어떤 conversationId를 사용하는지, 제목(subject)과 함께 저장.
 * <p>created_at, updated_at은 JPA Auditing으로 자동 채워진다.
 */
@Entity
@Table(name = "USER_CONVERSATION")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserConversationEntity {

    @EmbeddedId
    private UserConversationId id;

    @Column(name = "subject", nullable = false, columnDefinition = "TEXT")
    private String subject;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UserConversationEntity(String userId, String conversationId, String subject) {
        this.id = new UserConversationId(userId, conversationId);
        this.subject = subject != null ? subject : "(제목 없음)";
    }

    /** updatedAt만 현재 시각으로 갱신. 기존 대화에 새 메시지 보낼 때 save 전에 호출. */
    public void touch() {
        this.updatedAt = Instant.now();
    }

    /** 제목만 변경. 빈 값이면 "(제목 없음)"으로 넣는다. */
    public void changeSubject(String subject) {
        this.subject = (subject != null && !subject.isBlank()) ? subject : "(제목 없음)";
    }
}
