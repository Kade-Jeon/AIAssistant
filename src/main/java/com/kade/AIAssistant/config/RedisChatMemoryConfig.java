package com.kade.AIAssistant.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kade.AIAssistant.feature.conversation.repository.ChatMessageRepository;
import com.kade.AIAssistant.infra.redis.context.CustomChatMemoryRepository;
import com.kade.AIAssistant.infra.redis.context.RedisChatMemory;
import com.kade.AIAssistant.infra.redis.prompt.PromptCacheService;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * RedisChatMemory 및 RDB(ChatMemoryRepository) Bean 설정.
 * <p>RDB는 CustomChatMemoryRepository 사용 (CHAT_MESSAGE 테이블).
 */
@Configuration
public class RedisChatMemoryConfig {

    @Bean
    @Primary
    public ChatMemoryRepository chatMemoryRepository(CustomChatMemoryRepository customChatMemoryRepository) {
        return customChatMemoryRepository;
    }

    @Bean
    public RedisChatMemory redisChatMemory(
            PromptCacheService promptCacheService,
            ChatMemoryRepository chatMemoryRepository,
            ObjectMapper objectMapper,
            ChatMessageRepository chatMessageRepository,
            @Value("${app.conversation.cache-limit:20}") int cacheLimit
    ) {
        return new RedisChatMemory(promptCacheService, chatMemoryRepository, objectMapper, chatMessageRepository, cacheLimit);
    }

    @Bean
    @Primary
    public ChatMemory chatMemory(RedisChatMemory redisChatMemory) {
        return redisChatMemory;
    }
}
