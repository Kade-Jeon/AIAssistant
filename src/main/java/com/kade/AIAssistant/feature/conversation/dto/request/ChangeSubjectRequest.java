package com.kade.AIAssistant.feature.conversation.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 대화 제목 변경 PATCH 요청 본문.
 */
public record ChangeSubjectRequest(
        @NotBlank(message = "제목을 입력해주세요.")
        String subject
) {
}
