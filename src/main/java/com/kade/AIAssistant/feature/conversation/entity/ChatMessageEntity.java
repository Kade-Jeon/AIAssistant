package com.kade.AIAssistant.feature.conversation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Spring AI 채팅 메모리 테이블(SPRING_AI_CHAT_MEMORY) 매핑.
 * <p>Spring AI가 쓰는 테이블과 동일 스키마, 조회 전용.
 */
@Entity
@Table(name = "SPRING_AI_CHAT_MEMORY")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessageEntity {

    @EmbeddedId
    private ChatMessageId id;

    @Column(nullable = false, length = 10)
    private String type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
}
