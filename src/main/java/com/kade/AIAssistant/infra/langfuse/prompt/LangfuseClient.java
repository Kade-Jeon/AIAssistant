package com.kade.AIAssistant.infra.langfuse.prompt;

import com.kade.AIAssistant.common.enums.PromptType;
import com.kade.AIAssistant.feature.statistic.dto.response.ObservationResponse;
import com.kade.AIAssistant.infra.langfuse.constants.ObservationType;
import com.kade.AIAssistant.infra.langfuse.dto.response.LangfuseObservationsResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
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

    @Value("${langfuse.retry.max-attempts:3}")
    private int retryMaxAttempts;

    @Value("${langfuse.retry.delay-ms:2000}")
    private long retryDelayMs;

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
        log.info("[LangfuseClient] GET prompt type={} url={}", promptType, url);

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<LangfusePromptTemplate> response =
                restTemplate.exchange(url, HttpMethod.GET, entity, LangfusePromptTemplate.class);

        return response.getBody();
    }

    /**
     * 단일 구간(fromStartTime ~ toStartTime)에 대해 타입별 Langfuse 요청 후 병합 반환. 일자별 Redis 캐시 조회는 StatisticService에서 수행.
     */
    public List<ObservationResponse> getSpanObservationsByRange(String userId, Instant fromStartTime,
                                                                Instant toStartTime) {
        List<ObservationType> types = List.of(
                ObservationType.GENERATION,
                ObservationType.SPAN
        );
        List<ObservationResponse> merged = new ArrayList<>();
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

            log.debug("[LangfuseClient] GET observations type={} userId={} from={} to={}", type, userId, fromStartTime,
                    toStartTime);
            ResponseEntity<LangfuseObservationsResponse> response =
                    exchangeWithRetry(url, entity);

            LangfuseObservationsResponse body = response.getBody();
            if (body != null && body.data() != null) {
                merged.addAll(body.data());
            }
        }
        return merged;
    }

    /**
     * 5xx(524 등) 발생 시 설정된 횟수만큼 재시도. 모두 실패 시 마지막 예외를 그대로 전파.
     */
    private ResponseEntity<LangfuseObservationsResponse> exchangeWithRetry(
            String url,
            HttpEntity<Void> entity) {
        HttpServerErrorException lastException = null;
        for (int attempt = 1; attempt <= retryMaxAttempts; attempt++) {
            try {
                log.info("[LangfuseClient] GET observations attempt={}/{} url={}", attempt, retryMaxAttempts, url);
                return restTemplate.exchange(
                        url, HttpMethod.GET, entity, LangfuseObservationsResponse.class);
            } catch (HttpServerErrorException e) {
                lastException = e;
                if (attempt < retryMaxAttempts) {
                    log.warn("[LangfuseClient] observations API 5xx (attempt {}/{}), retry in {}ms: {}",
                            attempt, retryMaxAttempts, retryDelayMs, e.getMessage());
                    try {
                        Thread.sleep(retryDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Langfuse retry interrupted", ie);
                    }
                }
            }
        }
        throw lastException != null ? lastException : new IllegalStateException("retry exhausted");
    }
}
