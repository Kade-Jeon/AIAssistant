package com.kade.AIAssistant.feature.statistic.service;

import com.kade.AIAssistant.feature.statistic.dto.response.ObservationResponse;
import com.kade.AIAssistant.infra.langfuse.dto.response.LangfuseObservationsResponse;
import com.kade.AIAssistant.infra.langfuse.prompt.LangfuseClient;
import com.kade.AIAssistant.infra.redis.enums.RedisKeyPrefix;
import com.kade.AIAssistant.infra.redis.prompt.RedisCacheService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 사용자별 통계(observation 목록) 조회. days==0이면 당월 1일~현재 한 번 조회, days>0이면 타임존 기준 일자별 구간으로 나누어 Redis 캐시 조회 후 미스 시 Langfuse 호출·캐시
 * 후 병합 반환.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StatisticService {

    private final LangfuseClient langfuseClient;
    private final RedisCacheService cacheService;

    public List<ObservationResponse> getStatisticsByUserId(String userId, ZoneId timezone, int days) {
        Instant toStartTime = Instant.now();
        if (days == 0) {
            Instant fromStartTime = toStartTime
                    .atZone(timezone)
                    .toLocalDate()
                    .withDayOfMonth(1)
                    .atStartOfDay(timezone)
                    .toInstant();
            return langfuseClient.getSpanObservationsByRange(userId, fromStartTime, toStartTime);
        }

        // days > 0: 타임존 기준 6일전, 5일전, …, 1일전, 오늘(00:00~현재) 구간으로 나누어 Redis → Langfuse 후 병합
        LocalDate today = toStartTime.atZone(timezone).toLocalDate();
        List<ObservationResponse> merged = new ArrayList<>();

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            Instant from = date.atStartOfDay(timezone).toInstant();
            Instant to = (i == 0)
                    ? toStartTime
                    : date.plusDays(1).atStartOfDay(timezone).toInstant();

            List<ObservationResponse> forRange;
            if (i == 0) {
                // 오늘 날짜는 실시간 변동 가능하므로 캐시 없이 항상 API 호출
                forRange = langfuseClient.getSpanObservationsByRange(userId, from, to);
            } else {
                String cacheKey = buildStatisticCacheKey(userId, timezone, date);
                forRange = getFromCacheOrLangfuse(userId, cacheKey, from, to);
            }
            merged.addAll(forRange);
        }

        return merged;
    }

    /**
     * 해당 타임존 기준 날짜(00:00:00)를 키로 사용. 예: user_statistic:userId:Asia/Seoul:2026-01-28
     */
    private String buildStatisticCacheKey(String userId, ZoneId zoneId, LocalDate date) {
        return String.format("%s:%s:%s:%s",
                RedisKeyPrefix.USER_STATISTIC,
                userId,
                zoneId.getId(),
                date.toString());
    }

    private List<ObservationResponse> getFromCacheOrLangfuse(String userId, String cacheKey, Instant from, Instant to) {
        var cached = cacheService.get(cacheKey, LangfuseObservationsResponse.class);
        if (cached.isPresent()) {
            log.debug("[StatisticService] Redis 캐시 히트: {}", cacheKey);
            LangfuseObservationsResponse response = cached.get();
            cacheService.set(cacheKey, response, Duration.ofHours(24)); // 히트 시 TTL 갱신
            List<ObservationResponse> data = response.data();
            return data != null ? data : List.of();
        }

        log.debug("[StatisticService] Redis 캐시 미스, Langfuse 요청: {}", cacheKey);
        List<ObservationResponse> list = langfuseClient.getSpanObservationsByRange(userId, from, to);
        cacheService.set(cacheKey, new LangfuseObservationsResponse(list), Duration.ofHours(24));
        return list;
    }
}
