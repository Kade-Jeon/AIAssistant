package com.kade.AIAssistant.feature.login.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "이메일을 입력해주세요.")
        String emailId,

        @NotBlank(message = "비밀번호를 입력해주세요.")
        String password
) {
}
