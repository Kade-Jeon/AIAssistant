package com.kade.AIAssistant.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kade.AIAssistant.infra.redis.context.RedisChatMemory;
import com.kade.AIAssistant.infra.redis.prompt.PromptCacheService;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * RedisChatMemory 및 RDB(ChatMemoryRepository) Bean 설정.
 * <p>RDB는 JdbcChatMemoryRepository 사용. PostgreSQL 등 DB 연결 필요.
 */
@Configuration
public class RedisChatMemoryConfig {

    @Bean
    public ChatMemoryRepository chatMemoryRepository(JdbcTemplate jdbcTemplate) {
        return JdbcChatMemoryRepository.builder()
                .jdbcTemplate(jdbcTemplate)
                .build();
    }

    @Bean
    public RedisChatMemory redisChatMemory(
            PromptCacheService promptCacheService,
            ChatMemoryRepository chatMemoryRepository,
            ObjectMapper objectMapper
    ) {
        return new RedisChatMemory(promptCacheService, chatMemoryRepository, objectMapper);
    }

    @Bean
    @Primary
    public ChatMemory chatMemory(RedisChatMemory redisChatMemory) {
        return redisChatMemory;
    }
}
