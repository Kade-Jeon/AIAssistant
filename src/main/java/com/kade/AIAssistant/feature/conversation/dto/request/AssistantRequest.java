package com.kade.AIAssistant.feature.conversation.dto.request;

import com.kade.AIAssistant.common.enums.PromptType;
import jakarta.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public record AssistantRequest(
        @NotNull
        PromptType promptType,
        @NotBlank
        String question,
        String language,
        String conversationId,
        String subject,
        String projectId  // 프로젝트 대화 시 RAG 활성화용 (선택적)
) {
}
