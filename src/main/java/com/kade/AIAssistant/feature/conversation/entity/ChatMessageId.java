package com.kade.AIAssistant.feature.conversation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * SPRING_AI_CHAT_MEMORY 복합 키 (conversation_id + timestamp).
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ChatMessageId implements Serializable {

    @Column(name = "conversation_id", nullable = false, length = 36)
    private String conversationId;

    @Column(name = "\"timestamp\"", nullable = false)
    private Instant timestamp;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChatMessageId that = (ChatMessageId) o;
        return Objects.equals(conversationId, that.conversationId) && Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(conversationId, timestamp);
    }
}
