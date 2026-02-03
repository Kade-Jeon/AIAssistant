package com.kade.AIAssistant.feature.conversation.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * 표준화된 에러 응답 DTO
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    /**
     * 에러 코드 (예: AI_MODEL_ERROR, PROMPT_NOT_FOUND)
     */
    private final String errorCode;

    /**
     * 사용자에게 표시할 에러 메시지
     */
    private final String message;

    /**
     * 상세 에러 정보 (개발 환경에서만 포함)
     */
    private final String detail;

    /**
     * 에러 발생 시각
     */
    @Builder.Default
    private final LocalDateTime timestamp = LocalDateTime.now();

    /**
     * 요청 경로
     */
    private final String path;

    /**
     * HTTP 상태 코드
     */
    private final int status;

    /**
     * 간단한 에러 응답 생성
     */
    public static ErrorResponse of(String errorCode, String message, int status) {
        return ErrorResponse.builder()
                .errorCode(errorCode)
                .message(message)
                .status(status)
                .build();
    }

    /**
     * 상세 정보를 포함한 에러 응답 생성
     */
    public static ErrorResponse of(String errorCode, String message, String detail, String path, int status) {
        return ErrorResponse.builder()
                .errorCode(errorCode)
                .message(message)
                .detail(detail)
                .path(path)
                .status(status)
                .build();
    }
}
