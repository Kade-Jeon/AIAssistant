package com.kade.AIAssistant.domain.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

/**
 * SSE 스트리밍 에러 이벤트(event: error)의 data 필드 스펙.
 * 프론트엔드에서 재시도 여부 등을 판단할 수 있도록 구조화된 에러 응답.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SseErrorPayload {

    /**
     * 에러 코드 (예: STREAMING_FAILED, RETRY_EXHAUSTED)
     */
    private final String code;

    /**
     * 사용자에게 표시할 메시지
     */
    private final String message;

    /**
     * 재시도가 권장되는 일시적 오류인지 여부
     */
    private final Boolean retryable;

    public static SseErrorPayload of(String code, String message) {
        return SseErrorPayload.builder()
                .code(code)
                .message(message)
                .build();
    }

    public static SseErrorPayload of(String code, String message, boolean retryable) {
        return SseErrorPayload.builder()
                .code(code)
                .message(message)
                .retryable(retryable)
                .build();
    }
}
