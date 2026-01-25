package com.kade.AIAssistant.infra.langfuse.prompt;

import com.kade.AIAssistant.common.enums.PromptType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

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
        // API 경로는 보통 /api/public/v2/prompts/{name} 입니다.
        String url = baseUrl + "/api/public/v2/prompts/" + promptType.name();

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<LangfusePromptTemplate> response =
                restTemplate.exchange(url, HttpMethod.GET, entity, LangfusePromptTemplate.class);

        return response.getBody();
    }
}
