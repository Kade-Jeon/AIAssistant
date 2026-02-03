package com.kade.AIAssistant.infra.langfuse.dto.response;

import com.kade.AIAssistant.feature.statistic.dto.response.ObservationDto;
import java.util.List;

/**
 * Langfuse API observations 목록 조회 응답 body. API가 { "data": [ ... ] } 형태로 오므로 역직렬화용 래퍼만 필요.
 */
public record LangfuseObservationsResponse(
        List<ObservationDto> data
) {
}
