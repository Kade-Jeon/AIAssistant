package com.kade.AIAssistant.common.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kade.AIAssistant.common.enums.PromptType;
import com.kade.AIAssistant.common.exceptions.customs.PromptNotFoundException;
import com.kade.AIAssistant.infra.langfuse.prompt.LangfuseClient;
import com.kade.AIAssistant.infra.langfuse.prompt.LangfusePromptTemplate;
import com.kade.AIAssistant.infra.redis.enums.RedisKeyPrefix;
import com.kade.AIAssistant.infra.redis.prompt.RedisCacheService;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PromptTemplateProvider {

    private final RedisCacheService promptCacheService;
    private final LangfuseClient langfuseClient;
    private final ObjectMapper objectMapper;


    public LangfusePromptTemplate getSystemPromptTemplate(PromptType promptType) {
        log.info("[PromptTemplateProvider] Redis 캐시 조회: {}", promptType.name());

        String cacheKey = String.format("%s:{%s}", RedisKeyPrefix.SYSTEM_PROMPT, promptType.name());

        try {
            // 1. Redis에서 캐시 조회
            Optional<Object> cachedData = promptCacheService.get(cacheKey);

            LangfusePromptTemplate langfusePromptTemplate;
            if (cachedData.isPresent()) {
                // 2-2. 캐시가 있는 경우, JSON에서 객체로 변환
                log.info("[PromptTemplateProvider] Redis 캐시 히트: {}", promptType.name());
                String jsonData = (String) cachedData.get();
                langfusePromptTemplate = objectMapper.readValue(jsonData, LangfusePromptTemplate.class);
            } else {
                // 2-1. 캐시가 없는 경우, PromptClient로 요청 후 캐싱
                log.info("[PromptTemplateProvider] Redis 캐시 미스, Langfuse API 호출: {}", promptType.name());
                langfusePromptTemplate = langfuseClient.getPrompt(promptType);

                // JSON으로 직렬화해서 Redis에 저장
                String jsonData = objectMapper.writeValueAsString(langfusePromptTemplate);
                promptCacheService.set(cacheKey, jsonData, Duration.ofHours(1L));
                log.info("[PromptTemplateProvider] Redis 캐시 저장 완료: {}", promptType.name());
            }
            return langfusePromptTemplate;
        } catch (IOException e) {
            log.error("[PromptTemplateProvider] 파일 읽기 실패: {}", promptType.name(), e);
            throw new PromptNotFoundException(promptType, "파일 읽기 오류: " + e.getMessage());
        }
    }
}
