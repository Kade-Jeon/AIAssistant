package com.kade.AIAssistant.feature.preference.dto.reqeust;

import javax.validation.constraints.Max;

public record PreferenceRequest(
        @Max(value = 30)
        String nickname,
        @Max(value = 30)
        String occupation,
        @Max(value = 300)
        String extraInfo
) {
}