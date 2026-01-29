package com.kade.AIAssistant.feature.conversation.entity;

import com.kade.AIAssistant.common.enums.MessageType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 채팅 메시지 테이블(CHAT_MESSAGE) 매핑.
 * <p>안정적인 id로 모든 대화 히스토리를 저장합니다.
 * id는 변경되지 않으므로 CHAT_ATTACHMENT와 FK 제약이 가능합니다.
 * <p>CASCADE 삭제는 JPA에서 처리 (orphanRemoval = true).
 */
@Entity
@Table(name = "CHAT_MESSAGE")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessageEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "conversation_id", nullable = false, length = 36)
    private String conversationId;

    @Column(name = "\"timestamp\"", nullable = false)
    private Instant timestamp;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private MessageType type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChatAttachmentEntity> attachments = new ArrayList<>();

    public ChatMessageEntity(String conversationId, MessageType type, String content, Instant timestamp) {
        this.id = UUID.randomUUID();
        this.conversationId = conversationId;
        this.type = type;
        this.content = content;
        this.timestamp = timestamp;
    }

    /**
     * 호환성을 위한 생성자. String을 받아서 MessageType으로 변환합니다.
     */
    public ChatMessageEntity(String conversationId, String type, String content, Instant timestamp) {
        this(conversationId, MessageType.fromValue(type), content, timestamp);
    }
}
