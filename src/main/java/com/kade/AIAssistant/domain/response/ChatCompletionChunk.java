package com.kade.AIAssistant.domain.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 호환 스트리밍 청크 응답 DTO
 */
public record ChatCompletionChunk(
        String id,
        String object,
        Long created,
        String model,
        List<Choice> choices,
        Usage usage
) {
    public record Choice(
            Delta delta,
            Integer index,
            @JsonProperty("finish_reason")
            String finishReason
    ) {
    }

    public record Delta(
            String content,
            List<Map<String, Object>> toolCalls
    ) {
        public Delta(String content) {
            this(content, null);
        }
    }

    public record Usage(
            @JsonProperty("prompt_tokens")
            Integer promptTokens,
            @JsonProperty("completion_tokens")
            Integer completionTokens,
            @JsonProperty("total_tokens")
            Integer totalTokens
    ) {
    }

    /**
     * 일반 청크 생성
     */
    public static ChatCompletionChunk chunk(
            String id,
            Long created,
            String model,
            String content,
            List<Map<String, Object>> toolCalls,
            String finishReason
    ) {
        Delta delta;
        if (toolCalls != null && !toolCalls.isEmpty()) {
            delta = new Delta(content, toolCalls);
        } else if (content != null && !content.isEmpty()) {
            delta = new Delta(content);
        } else {
            delta = new Delta("");
        }

        String finishReasonValue = (finishReason != null && !finishReason.equals("null"))
                ? finishReason
                : null;

        Choice choice = new Choice(delta, 0, finishReasonValue);
        return new ChatCompletionChunk(id, "chat.completion.chunk", created, model, List.of(choice), null);
    }

    /**
     * 완료 청크 생성 (빈 delta, usage 포함)
     */
    public static ChatCompletionChunk completion(
            String id,
            Long created,
            String model,
            String finishReason,
            Usage usage
    ) {
        Delta delta = new Delta("");
        String finishReasonValue = (finishReason != null && !finishReason.equals("null"))
                ? finishReason
                : "stop";
        Choice choice = new Choice(delta, 0, finishReasonValue);
        return new ChatCompletionChunk(id, "chat.completion.chunk", created, model, List.of(choice), usage);
    }
}
