package com.kade.AIAssistant.domain.response;

import java.util.List;
import java.util.Map;

/**
 * 처리된 스트리밍 청크 DTO
 */
public record ProcessedChunk(
        String content,
        StreamingSessionInfo sessionInfo,
        List<Map<String, Object>> toolCalls
) {
    /**
     * tool_calls가 없는 경우를 위한 편의 생성자
     */
    public ProcessedChunk(String content, StreamingSessionInfo sessionInfo) {
        this(content, sessionInfo, null);
    }
}
