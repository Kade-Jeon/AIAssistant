package com.kade.AIAssistant.feature.project.dto.reqeust;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank(message = "프로젝트 이름을 입력해주세요.")
        @Size(min = 1, max = 32)
        String subject
) {
}