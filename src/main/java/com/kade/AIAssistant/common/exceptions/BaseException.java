package com.kade.AIAssistant.common.exceptions;

import lombok.Getter;

/**
 * 애플리케이션의 모든 커스텀 예외의 기본 클래스
 */
@Getter
public class BaseException extends RuntimeException {

    private final String errorCode;
    private final int httpStatus;

    public BaseException(String message, String errorCode, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public BaseException(String message, Throwable cause, String errorCode, int httpStatus) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
}
