package com.kade.AIAssistant.common.enums;

import com.kade.AIAssistant.feature.conversation.dto.request.AssistantRequest;
import java.util.Map;

public enum PromptType {
    CONVERSATION {
        @Override
        public Map<String, Object> formatVariable(AssistantRequest contents) {
            return Map.of();
        }
    },

    PROJECT {
        @Override
        public Map<String, Object> formatVariable(AssistantRequest contents) {
            return Map.of();
        }
    },

    SUBJECT {
        @Override
        public Map<String, Object> formatVariable(AssistantRequest contents) {
            return Map.of();
        }
    };

    /**
     * SystemTemplate의 문자열을 치환할 수 있도록, Map을 반환합니다. (Spring AI 권장 방법 활용)
     */
    public abstract Map<String, Object> formatVariable(AssistantRequest contents);
}
