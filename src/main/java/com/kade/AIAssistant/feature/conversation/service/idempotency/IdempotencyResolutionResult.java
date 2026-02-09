package com.kade.AIAssistant.feature.conversation.service.idempotency;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Idempotency 처리 결과를 담는 DTO.
 * conversationId와 사용자 메시지 저장 스킵 여부를 포함.
 */
@Getter
@AllArgsConstructor
public class IdempotencyResolutionResult {

    /**
     * 결정된 대화 ID
     */
    private final String conversationId;

    /**
     * 사용자 메시지 저장을 스킵할지 여부
     */
    private final boolean skipSaveUserMessage;

    /**
     * SSE 스트림 완료 여부 (COMPLETED 상태인 경우 true)
     */
    private final boolean alreadyCompleted;
}
