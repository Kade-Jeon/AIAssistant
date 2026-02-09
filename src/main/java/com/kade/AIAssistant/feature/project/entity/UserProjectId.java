package com.kade.AIAssistant.feature.project.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * USER_PROJECT 복합 키 (user_id + project_id).
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class UserProjectId implements Serializable {

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "conversation_id", nullable = false, length = 36)
    private String conversationId;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UserProjectId that = (UserProjectId) o;
        return Objects.equals(userId, that.userId) && Objects.equals(conversationId, that.conversationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, conversationId);
    }
}
