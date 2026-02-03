package com.kade.AIAssistant.feature.statistic.service;

import com.kade.AIAssistant.feature.statistic.dto.response.ObservationDto;
import com.kade.AIAssistant.infra.langfuse.prompt.LangfuseClient;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 사용자별 통계(observation 목록)를 조회하는 서비스. Langfuse 등 외부 API 연동 시 이 레이어에서 데이터를 채워 반환한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StatisticService {

    private final LangfuseClient langfuseClient;

    public List<ObservationDto> getStatisticsByUserId(String userId, ZoneId timezone) {
        return langfuseClient.getSpanObservationsByUserId(userId, timezone);
    }
}
