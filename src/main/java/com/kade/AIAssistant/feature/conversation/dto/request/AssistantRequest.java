package com.kade.AIAssistant.feature.conversation.dto.request;

import com.kade.AIAssistant.common.enums.PromptType;
import jakarta.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public record AssistantRequest(
        @NotNull
        PromptType promptType,
        @NotBlank
        String question,
        String conversationId,
        String subject
) {
}
