package com.kade.AIAssistant.feature.statistic.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Langfuse/OpenTelemetry observation metadata (attributes, resourceAttributes, scope).
 * 필요시 사용.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ObservationMetadataDto(
        Map<String, Object> attributes,
        Map<String, Object> resourceAttributes,
        ObservationMetadataScopeDto scope
) {
}
