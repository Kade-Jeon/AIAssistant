package com.kade.AIAssistant.common.exceptions.customs;


import com.kade.AIAssistant.common.exceptions.BaseException;

/**
 * 요청한 AI 모델을 찾을 수 없을 때 발생하는 예외
 */
public class ModelNotFoundException extends BaseException {

    public ModelNotFoundException(String modelName) {
        super("AI 모델을 찾을 수 없습니다: " + modelName, "MODEL_NOT_FOUND", 404);
    }

    public ModelNotFoundException(String modelName, Throwable cause) {
        super("AI 모델을 찾을 수 없습니다: " + modelName, cause, "MODEL_NOT_FOUND", 404);
    }
}

