package com.kade.AIAssistant.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class OllamaConfig {

    @Value("${spring.ai.ollama.base-url}")
    private String OLLAMA_BASE_URL;


    @Bean
    public OllamaApi ollamaApi() {
        log.info("Ollama API 클라이언트 초기화");
        return new OllamaApi.Builder()
                .baseUrl(OLLAMA_BASE_URL)
                .build();
    }

}
