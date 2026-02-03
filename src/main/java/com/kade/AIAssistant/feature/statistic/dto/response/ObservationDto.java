package com.kade.AIAssistant.feature.statistic.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/**
 * Langfuse/OpenTelemetry observation(span) 단일 항목 DTO.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ObservationDto(
        String id,
        String traceId,
        Instant startTime,
        String projectId,
        String parentObservationId,
        String type,
        String environment,
        Instant endTime,
        String name,
        String level,
        String statusMessage,
        String version,
        Instant createdAt,
        Instant updatedAt,
        Object input,
        Object output,
        String model,
        Object modelParameters,
        Instant completionStartTime,
        String promptId,
        String promptName,
        String promptVersion,
        Double latency,
        Double timeToFirstToken,
        Map<String, Object> usageDetails,
        Map<String, Object> costDetails,
        String usagePricingTierId,
        String usagePricingTierName,
        String modelId,
        Double inputPrice,
        Double outputPrice,
        Double totalPrice,
        Double calculatedInputCost,
        Double calculatedOutputCost,
        Double calculatedTotalCost,
        String unit,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        ObservationUsageDto usage
) {
}
