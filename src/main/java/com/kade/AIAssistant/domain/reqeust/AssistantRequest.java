package com.kade.AIAssistant.domain.reqeust;


import com.kade.AIAssistant.common.enums.PromptType;
import jakarta.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public record AssistantRequest(
        @NotNull
        PromptType promptType,
        @NotBlank
        String question,
        String language,
        String targetType,
        String toneType,
        String userId,
        String sessionId,
        String tenant
) {
}
