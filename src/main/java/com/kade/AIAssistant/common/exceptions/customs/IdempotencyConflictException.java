package com.kade.AIAssistant.common.exceptions.customs;

import com.kade.AIAssistant.common.exceptions.BaseException;

/**
 * 동일한 Idempotency-Key로 요청이 이미 처리 중이거나 완료된 경우 (409 Conflict)
 */
public class IdempotencyConflictException extends BaseException {

    public static final String CODE_REQUEST_IN_PROGRESS = "REQUEST_IN_PROGRESS";

    public IdempotencyConflictException(String message, String errorCode) {
        super(message, errorCode, 409);
    }
}
