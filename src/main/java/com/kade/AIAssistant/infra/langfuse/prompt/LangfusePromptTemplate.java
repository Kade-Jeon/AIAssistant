package com.kade.AIAssistant.infra.langfuse.prompt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.ai.ollama.api.OllamaChatOptions;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LangfusePromptTemplate(
        String name,
        Integer version,
        String prompt,
        PromptOptions config
) {
    public OllamaChatOptions getOllamaChatOptions(String modelName) {
        var builder = OllamaChatOptions.builder();
        builder.model(modelName);

        if (config.temperature() != null) {
            builder.temperature(config.temperature());
        }
        if (config.topK() != null) {
            builder.topK(config.topK());
        }
        if (config.topP() != null) {
            builder.topP(config.topP());
        }
        if (config.presencePenalty() != null) {
            builder.presencePenalty(config.presencePenalty());
        }
        if (config.frequencyPenalty() != null) {
            builder.frequencyPenalty(config.frequencyPenalty());
        }
        if (config.thinkLevel() != null) {
            builder.thinkOption(config.getThinkLevel());
        }

        return builder.build();
    }
}