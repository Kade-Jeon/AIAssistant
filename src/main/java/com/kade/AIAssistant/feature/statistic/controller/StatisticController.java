package com.kade.AIAssistant.feature.statistic.controller;

import com.kade.AIAssistant.feature.preference.dto.reqeust.PreferenceRequest;
import com.kade.AIAssistant.feature.statistic.dto.response.ObservationDto;
import com.kade.AIAssistant.feature.statistic.service.StatisticService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
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
    public ResponseEntity<List<ObservationDto>> getStaticsByUserId(
            @RequestHeader(value = "USER-ID") String userId,
            @RequestHeader(value = "X-USER-TIMEZONE") String timezoneHeader,
            @RequestBody(required = false) @Valid PreferenceRequest request,
            HttpServletRequest httpRequest
    ) {
        ZoneId timezone = (timezoneHeader != null && !timezoneHeader.isBlank())
                ? ZoneId.of(timezoneHeader.strip())
                : ZoneId.systemDefault();
        List<ObservationDto> data = statisticService.getStatisticsByUserId(userId, timezone);
        return ResponseEntity.ok(data);
    }
}
