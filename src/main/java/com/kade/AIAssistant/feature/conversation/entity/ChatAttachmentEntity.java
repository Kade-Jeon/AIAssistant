package com.kade.AIAssistant.feature.conversation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 채팅 메시지 첨부파일 메타데이터.
 * CHAT_MESSAGE.id로 메시지와 연결 (FK 제약).
 * <p>CASCADE 삭제는 JPA에서 처리 (부모 엔티티의 orphanRemoval = true).
 */
@Entity
@Table(name = "CHAT_ATTACHMENT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatAttachmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "message_id", nullable = false, updatable = false)
    private ChatMessageEntity message;

    /**
     * 호환성을 위한 메서드. message.getId()를 반환합니다.
     */
    public UUID getMessageId() {
        return message != null ? message.getId() : null;
    }

    @Column(name = "conversation_id", nullable = false, length = 36)
    private String conversationId;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(nullable = false)
    private Long size;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public ChatAttachmentEntity(ChatMessageEntity message, String conversationId, String filename, String mimeType, Long size) {
        this.message = message;
        this.conversationId = conversationId;
        this.filename = filename;
        this.mimeType = mimeType;
        this.size = size;
        this.createdAt = Instant.now();
    }

}
