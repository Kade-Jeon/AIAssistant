package com.kade.AIAssistant.feature.conversation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 채팅 메시지 테이블(CHAT_MESSAGE) 매핑.
 * <p>안정적인 message_id로 모든 대화 히스토리를 저장합니다.
 * message_id는 변경되지 않으므로 CHAT_ATTACHMENT와 FK 제약이 가능합니다.
 */
@Entity
@Table(name = "CHAT_MESSAGE")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessageEntity {

    @Id
    @Column(name = "message_id", nullable = false, updatable = false)
    private UUID messageId;

    @Column(name = "conversation_id", nullable = false, length = 36)
    private String conversationId;

    @Column(name = "\"timestamp\"", nullable = false)
    private Instant timestamp;

    @Column(nullable = false, length = 10)
    private String type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ChatMessageEntity(String conversationId, String type, String content, Instant timestamp) {
        this.messageId = UUID.randomUUID();
        this.conversationId = conversationId;
        this.type = type;
        this.content = content;
        this.timestamp = timestamp;
        this.createdAt = Instant.now();
    }
}
