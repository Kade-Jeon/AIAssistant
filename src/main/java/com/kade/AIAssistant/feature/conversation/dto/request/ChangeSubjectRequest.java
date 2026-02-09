package com.kade.AIAssistant.feature.conversation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 대화 제목 변경 PATCH 요청 본문.
 */
public record ChangeSubjectRequest(
        @NotBlank(message = "제목을 입력해주세요.")
        @Size(min = 1, max = 32)
        String subject
) {
}
