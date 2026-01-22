package com.kade.AIAssistant.config;

import com.kade.AIAssistant.infra.langfuse.observability.ChatModelCompletionContentObservationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservabilityConfig {

    @Bean
    public ChatModelCompletionContentObservationFilter chatModelCompletionContentObservationFilter() {
        return new ChatModelCompletionContentObservationFilter();
    }
}
