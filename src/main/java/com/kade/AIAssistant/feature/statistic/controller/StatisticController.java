package com.kade.AIAssistant.feature.statistic.controller;

import com.kade.AIAssistant.feature.statistic.dto.response.ObservationResponse;
import com.kade.AIAssistant.feature.statistic.service.StatisticService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/ai/stat")
public class StatisticController {

    private final StatisticService statisticService;

    /**
     * 사용자별 통계(observation 목록) 조회. 응답은 [ ... ] 배열.
     */
    @GetMapping("")
    public ResponseEntity<List<ObservationResponse>> getStaticsByUserId(
            @RequestHeader(value = "USER-ID") String userId,
            @RequestHeader(value = "X-USER-TIMEZONE") String timezoneHeader,
            @RequestParam(value = "days", defaultValue = "7") int days,
            HttpServletRequest httpRequest
    ) {
        ZoneId timezone = (timezoneHeader != null && !timezoneHeader.isBlank())
                ? ZoneId.of(timezoneHeader.strip())
                : ZoneId.systemDefault();
        List<ObservationResponse> data = statisticService.getStatisticsByUserId(userId, timezone, days);
        return ResponseEntity.ok(data);
    }
}
