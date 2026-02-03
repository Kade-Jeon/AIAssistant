package com.kade.AIAssistant.feature.statistic.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Langfuse observation usage (unit, input, output, total).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ObservationUsageDto(
        String unit,
        Integer input,
        Integer output,
        Integer total
) {
}
