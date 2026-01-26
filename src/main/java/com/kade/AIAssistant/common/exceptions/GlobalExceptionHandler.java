package com.kade.AIAssistant.common.exceptions;

import com.kade.AIAssistant.common.exceptions.customs.AiModelException;
import com.kade.AIAssistant.common.exceptions.customs.ForbiddenException;
import com.kade.AIAssistant.common.exceptions.customs.InvalidRequestException;
import com.kade.AIAssistant.common.exceptions.customs.ModelNotFoundException;
import com.kade.AIAssistant.common.exceptions.customs.PromptNotFoundException;
import com.kade.AIAssistant.domain.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 전역 예외 처리 핸들러 모든 컨트롤러에서 발생하는 예외를 중앙에서 처리합니다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * AI 모델 관련 예외 처리
     */
    @ExceptionHandler(AiModelException.class)
    public ResponseEntity<ErrorResponse> handleAiModelException(
            AiModelException e,
            HttpServletRequest request) {

        log.error("AI 모델 오류 발생: {}", e.getMessage(), e);

        ErrorResponse errorResponse = ErrorResponse.of(
                e.getErrorCode(),
                e.getMessage(),
                e.getCause() != null ? e.getCause().getMessage() : null,
                request.getRequestURI(),
                e.getHttpStatus()
        );

        return ResponseEntity
                .status(e.getHttpStatus())
                .body(errorResponse);
    }

    /**
     * 모델을 찾을 수 없는 경우
     */
    @ExceptionHandler(ModelNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleModelNotFoundException(
            ModelNotFoundException e,
            HttpServletRequest request) {

        log.warn("모델을 찾을 수 없음: {}", e.getMessage());

        ErrorResponse errorResponse = ErrorResponse.of(
                e.getErrorCode(),
                e.getMessage(),
                null,
                request.getRequestURI(),
                HttpStatus.NOT_FOUND.value()
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(errorResponse);
    }

    /**
     * 프롬프트를 찾을 수 없는 경우
     */
    @ExceptionHandler(PromptNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePromptNotFoundException(
            PromptNotFoundException e,
            HttpServletRequest request) {

        log.warn("프롬프트를 찾을 수 없음: {}", e.getMessage());

        ErrorResponse errorResponse = ErrorResponse.of(
                e.getErrorCode(),
                e.getMessage(),
                null,
                request.getRequestURI(),
                HttpStatus.NOT_FOUND.value()
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(errorResponse);
    }


    /**
     * 접근 권한 없음 (403)
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbiddenException(
            ForbiddenException e,
            HttpServletRequest request) {

        log.warn("접근 거부: {}", e.getMessage());

        ErrorResponse errorResponse = ErrorResponse.of(
                e.getErrorCode(),
                e.getMessage(),
                null,
                request.getRequestURI(),
                HttpStatus.FORBIDDEN.value()
        );

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(errorResponse);
    }

    /**
     * 잘못된 요청 처리
     */
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequestException(
            InvalidRequestException e,
            HttpServletRequest request) {

        log.warn("잘못된 요청: {}", e.getMessage());

        ErrorResponse errorResponse = ErrorResponse.of(
                e.getErrorCode(),
                e.getMessage(),
                null,
                request.getRequestURI(),
                HttpStatus.BAD_REQUEST.value()
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }

    /**
     * 타입 불일치 예외 처리
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException e,
            HttpServletRequest request) {

        log.warn("잘못된 파라미터 타입: {}", e.getMessage());

        String message = String.format("파라미터 '%s'의 값이 올바르지 않습니다.", e.getName());

        ErrorResponse errorResponse = ErrorResponse.of(
                "INVALID_PARAMETER",
                message,
                e.getMessage(),
                request.getRequestURI(),
                HttpStatus.BAD_REQUEST.value()
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }

    /**
     * IllegalArgumentException 처리
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException e,
            HttpServletRequest request) {

        log.warn("잘못된 인자: {}", e.getMessage());

        ErrorResponse errorResponse = ErrorResponse.of(
                "INVALID_ARGUMENT",
                e.getMessage(),
                null,
                request.getRequestURI(),
                HttpStatus.BAD_REQUEST.value()
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }

    /**
     * IllegalStateException 처리
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(
            IllegalStateException e,
            HttpServletRequest request) {

        log.error("잘못된 상태: {}", e.getMessage(), e);

        ErrorResponse errorResponse = ErrorResponse.of(
                "ILLEGAL_STATE",
                e.getMessage(),
                null,
                request.getRequestURI(),
                HttpStatus.INTERNAL_SERVER_ERROR.value()
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse);
    }

    /**
     * IOException 처리 - SSE 클라이언트 연결 종료 등 SSE 엔드포인트에서 클라이언트가 연결을 끊은 경우만 무시합니다.
     */
    @ExceptionHandler(IOException.class)
    public ResponseEntity<ErrorResponse> handleIOException(
            IOException e,
            HttpServletRequest request) {

        String message = e.getMessage();
        String uri = request.getRequestURI();

        // SSE 엔드포인트에서의 클라이언트 연결 종료만 무시
        boolean isSseEndpoint = uri != null && (
                uri.startsWith("/api/function") ||  // SSE 스트리밍 엔드포인트
                        "text/event-stream".equals(request.getHeader("Accept"))
        );

        boolean isClientDisconnect = message != null && (
                message.contains("현재 연결은 사용자의 호스트 시스템의 소프트웨어의 의해 중단되었습니다") ||
                        message.contains("Connection reset") ||
                        message.contains("Broken pipe") ||
                        message.contains("An established connection was aborted") ||
                        message.contains("클라이언트에서 연결을 끊었습니다")
        );

        // SSE 엔드포인트에서의 클라이언트 연결 종료는 무시
        if (isSseEndpoint && isClientDisconnect) {
            log.debug("SSE 클라이언트 연결 종료 무시: {} - {}", uri, message);
            return ResponseEntity.ok().build();
        }

        // 그 외 IOException은 ERROR 로그 출력 (중요한 오류일 수 있음)
        log.error("IOException 발생: {} - {}", uri, message, e);

        ErrorResponse errorResponse = ErrorResponse.of(
                "IO_ERROR",
                "입출력 오류가 발생했습니다.",
                message,
                uri,
                HttpStatus.INTERNAL_SERVER_ERROR.value()
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse);
    }

    /**
     * 모든 예외의 최종 처리 (Fallback)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAllException(
            Exception e,
            HttpServletRequest request) {

        String uri = request.getRequestURI();

        // SSE 엔드포인트 여부 확인
        boolean isSseEndpoint = uri != null && (
                uri.startsWith("/api/function") ||
                        "text/event-stream".equals(request.getHeader("Accept"))
        );

        // IOException이 원인인 경우 (SSE 클라이언트 연결 종료 등)
        Throwable cause = e.getCause();
        if (isSseEndpoint && cause instanceof IOException) {
            String message = cause.getMessage();
            if (message != null && (
                    message.contains("현재 연결은 사용자의 호스트 시스템의 소프트웨어의 의해 중단되었습니다") ||
                            message.contains("Connection reset") ||
                            message.contains("Broken pipe") ||
                            message.contains("An established connection was aborted") ||
                            message.contains("클라이언트에서 연결을 끊었습니다"))) {
                log.debug("SSE 클라이언트 연결 종료로 인한 예외 무시: {}", uri);
                return ResponseEntity.ok().build();
            }
        }

        log.error("예상치 못한 오류 발생: {}", e.getMessage(), e);

        ErrorResponse errorResponse = ErrorResponse.of(
                "INTERNAL_SERVER_ERROR",
                "서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
                e.getMessage(),
                uri,
                HttpStatus.INTERNAL_SERVER_ERROR.value()
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse);
    }
}
