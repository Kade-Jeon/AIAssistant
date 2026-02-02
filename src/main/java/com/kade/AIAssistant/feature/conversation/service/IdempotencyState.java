package com.kade.AIAssistant.feature.conversation.service;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Idempotency-Key별 Redis 저장 값.
 * Redis value 직렬화용 (GenericJackson2JsonRedisSerializer).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyState implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";

    /**
     * 상태: IN_PROGRESS, COMPLETED, FAILED
     */
    private String status;

    /**
     * 대화 ID (스트리밍에 사용)
     */
    private String conversationId;

    /**
     * 저장된 USER 메시지 ID (선택)
     */
    private UUID userMessageId;
}
