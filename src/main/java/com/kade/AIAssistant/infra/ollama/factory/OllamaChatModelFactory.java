package com.kade.AIAssistant.infra.ollama.factory;

import com.kade.AIAssistant.common.enums.PromptType;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class OllamaChatModelFactory {

    private final OllamaApi ollamaApi;
    private final ObservationRegistry observationRegistry;


    /**
     * 모델을 동적으로 생성하되, Spring Cache를 사용하여 재사용 옵션 정보를 캐시 키에 포함하여 정확한 모델 반환 보장
     */
    @Cacheable(value = "chatModels", key = "#modelName + ':' + #promptType.name() + ':' + #options.hashCode()")
    public OllamaChatModel getChatModel(String modelName, PromptType promptType, OllamaChatOptions options) {
        log.info("새로운 ChatModel 생성 및 캐싱: model={}, promptType={}, options={}",
                modelName, promptType, options);

        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(options)
                .observationRegistry(observationRegistry)
                .build();
    }
}
