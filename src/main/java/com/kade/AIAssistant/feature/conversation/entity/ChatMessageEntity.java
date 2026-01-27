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
 * Spring AI 채팅 메모리 테이블(SPRING_AI_CHAT_MEMORY) 매핑.
 * <p>Spring AI는 conversation_id, content, type, timestamp 4컬럼만 INSERT. message_id는 DB DEFAULT로 자동 생성.
 */
@Entity
@Table(name = "SPRING_AI_CHAT_MEMORY")
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
}
