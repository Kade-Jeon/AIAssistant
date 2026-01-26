package com.kade.AIAssistant.domain.response;

public record UsageInfo(
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens
) {
    public static UsageInfo of(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        return new UsageInfo(promptTokens, completionTokens, totalTokens);
    }
}
