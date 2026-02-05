package com.kade.AIAssistant.feature.statistic.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/**
 * Langfuse ObservationsView 스키마. Observation + promptName, promptVersion, modelId, prices, latency, timeToFirstToken.
 * 현재 단계에서 불필요한 필드는 주석처리.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ObservationResponse(
        // Observation (base)
        String id,
        String traceId,
        String type,
        String name,
        Instant startTime,
        Instant endTime,
//        Instant completionStartTime,
        String model,
//        Object modelParameters,
//        Object input,
//        String version,
//        Object metadata,
//        Object output,
        String level,
        String statusMessage,
        String parentObservationId,
//        String promptId,
        Map<String, Object> usageDetails,
        Map<String, Object> costDetails,
//        String environment,
        // ObservationsView 추가 필드
//        String promptName,
//        Integer promptVersion,
//        String modelId,
//        Double inputPrice,
//        Double outputPrice,
//        Double totalPrice,
//        Double calculatedInputCost,
//        Double calculatedOutputCost,
//        Double calculatedTotalCost,
//        Double timeToFirstToken,
        Double latency
) {
}
