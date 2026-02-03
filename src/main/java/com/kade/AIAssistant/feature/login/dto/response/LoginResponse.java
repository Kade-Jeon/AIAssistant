package com.kade.AIAssistant.feature.login.dto.response;

import com.kade.AIAssistant.common.enums.UserPlan;
import java.util.UUID;

public record LoginResponse(
        UUID userId,
        String emailId,
        UserPlan plan
) {
}
