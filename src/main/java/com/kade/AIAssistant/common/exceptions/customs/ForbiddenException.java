package com.kade.AIAssistant.common.exceptions.customs;

import com.kade.AIAssistant.common.exceptions.BaseException;

/**
 * 접근 권한이 없을 때 (403 Forbidden)
 */
public class ForbiddenException extends BaseException {

    public ForbiddenException(String message) {
        super(message, "FORBIDDEN", 403);
    }
}
