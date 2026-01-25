package com.kade.AIAssistant.common.exceptions.customs;


import com.kade.AIAssistant.common.exceptions.BaseException;

/**
 * 잘못된 요청 데이터에 대한 예외
 */
public class InvalidRequestException extends BaseException {

    public InvalidRequestException(String message) {
        super(message, "INVALID_REQUEST", 400);
    }
}

