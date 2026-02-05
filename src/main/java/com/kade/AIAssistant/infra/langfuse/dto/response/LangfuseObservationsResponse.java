package com.kade.AIAssistant.infra.langfuse.dto.response;

import com.kade.AIAssistant.feature.statistic.dto.response.ObservationResponse;
import java.util.List;

/**
 * Langfuse API observations 목록 조회 응답 (ObservationsViews 스키마). API 응답: { "data": [ ... ], "meta": { page, limit,
 * totalItems, totalPages } }
 * <p>
 * meta는 필요 시 추가 예정.
 */
public record LangfuseObservationsResponse(
        List<ObservationResponse> data
//        ObservationsMetaDto meta
) {
}