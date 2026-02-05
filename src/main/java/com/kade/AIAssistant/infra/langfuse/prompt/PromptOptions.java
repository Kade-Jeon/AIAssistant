package com.kade.AIAssistant.infra.langfuse.prompt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.ai.ollama.api.ThinkOption.ThinkLevel;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PromptOptions(
        String model,
        Double temperature,
        Integer topK,
        Double topP,
        Double frequencyPenalty,
        Double presencePenalty,
        String thinkLevel
) {
    /**
     * String 형태의 thinkOption을 실제 라이브러리의 ThinkLevel 객체로 변환하여 반환
     */
    public ThinkLevel getThinkLevel() {
        if (thinkLevel == null) {
            return null;
        }
        // ThinkLevel 생성자나 팩토리 메서드 사용
        return new ThinkLevel(thinkLevel.toLowerCase());
    }
}
