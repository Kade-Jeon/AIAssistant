package com.kade.AIAssistant.common.exceptions.customs;


import com.kade.AIAssistant.common.enums.PromptType;
import com.kade.AIAssistant.common.exceptions.BaseException;

/**
 * 프롬프트 템플릿을 찾을 수 없을 때 발생하는 예외
 */
public class PromptNotFoundException extends BaseException {

    public PromptNotFoundException(PromptType promptType) {
        super("프롬프트 정보를 찾을 수 없습니다: " + promptType, "PROMPT_NOT_FOUND", 404);
    }

    public PromptNotFoundException(PromptType promptType, String additionalMessage) {
        super("프롬프트 정보를 찾을 수 없습니다: " + promptType + " - " + additionalMessage, "PROMPT_NOT_FOUND", 404);
    }

    public PromptNotFoundException(String message) {
        super(message, "PROMPT_NOT_FOUND", 404);
    }
}

