package com.kade.AIAssistant.infra.langfuse.prompt;

import com.kade.AIAssistant.common.enums.PromptType;
import com.kade.AIAssistant.feature.statistic.dto.response.ObservationDto;
import com.kade.AIAssistant.infra.langfuse.constants.ObservationType;
import com.kade.AIAssistant.infra.langfuse.dto.response.LangfuseObservationsResponse;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * https://api.reference.langfuse.com/
 */
@Component
@Slf4j
public class LangfuseClient {

    private final RestTemplate restTemplate;
    private final String baseUrl = "http://localhost:3000";
    private final HttpHeaders headers;

    public LangfuseClient(
            @Value("${langfuse.public-key}") String publicKey,
            @Value("${langfuse.secret-key}") String secretKey
    ) {
        this.restTemplate = new RestTemplate();
        this.headers = new HttpHeaders();
        this.headers.setBasicAuth(publicKey, secretKey);
    }

    public LangfusePromptTemplate getPrompt(PromptType promptType) {
        String url = baseUrl + "/api/public/v2/prompts/" + promptType.name();

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<LangfusePromptTemplate> response =
                restTemplate.exchange(url, HttpMethod.GET, entity, LangfusePromptTemplate.class);

        return response.getBody();
    }

    /**
     * 타입별로 요청한 뒤 응답 목록을 하나로 합쳐 반환.
     */
    public List<ObservationDto> getSpanObservationsByUserId(String userId, ZoneId timezone) {
        // 해당 타임존 기준 이번 달 1일 00:00:00 → Instant
        Instant toStartTime = Instant.now();
        Instant fromStartTime = toStartTime
                .atZone(timezone)
                .toLocalDate()
                .withDayOfMonth(1)
                .atStartOfDay(timezone)
                .toInstant();

        // 수집할 타입 목록
        List<ObservationType> types = List.of(
                ObservationType.GENERATION,
                ObservationType.SPAN
        );

        List<ObservationDto> merged = new ArrayList<>();
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        for (ObservationType type : types) {
            String url = UriComponentsBuilder.fromUriString(baseUrl)
                    .path("/api/public/observations")
                    .queryParam("userId", userId)
                    .queryParam("type", type)
                    .queryParam("fromStartTime", fromStartTime.toString())
                    .queryParam("toStartTime", toStartTime.toString())
                    .build()
                    .toUriString();

            ResponseEntity<LangfuseObservationsResponse> response =
                    restTemplate.exchange(url, HttpMethod.GET, entity, LangfuseObservationsResponse.class);

            LangfuseObservationsResponse body = response.getBody();
            if (body != null && body.data() != null) {
                merged.addAll(body.data());
            }
        }

        return merged;
    }
}
