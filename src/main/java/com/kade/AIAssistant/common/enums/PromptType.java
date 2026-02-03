package com.kade.AIAssistant.common.enums;

import com.kade.AIAssistant.common.constants.PromptVariables;
import com.kade.AIAssistant.feature.conversation.dto.request.AssistantRequest;
import java.util.HashMap;
import java.util.Map;

public enum PromptType {
    TRANSLATE {
        @Override
        public Map<String, Object> formatVariable(AssistantRequest contents) {
            Map<String, Object> map = new HashMap<>();
            if (contents.language() != null) {
                map.put(PromptVariables.LANGUAGE, Language.toKoreanName(contents.language()));
            }
            return map;
        }
    },

    CONVERSATION {
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
