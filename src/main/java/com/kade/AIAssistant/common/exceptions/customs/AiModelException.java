package com.kade.AIAssistant.common.exceptions.customs;


import com.kade.AIAssistant.common.exceptions.BaseException;

/**
 * AI 모델 호출 중 발생하는 예외
 */
public class AiModelException extends BaseException {

    public AiModelException(String message) {
        super(message, "AI_MODEL_ERROR", 500);
    }

    public AiModelException(String message, Throwable cause) {
        super(message, cause, "AI_MODEL_ERROR", 500);
    }
}

