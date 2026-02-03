package com.kade.AIAssistant.feature.statistic.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Langfuse/OpenTelemetry observation metadata 내 scope 정보.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ObservationMetadataScopeDto(
        String name,
        String version,
        Map<String, Object> attributes
) {
}
