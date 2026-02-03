package com.kade.AIAssistant.common.utils;

import com.kade.AIAssistant.feature.conversation.dto.response.ProcessedChunk;
import com.kade.AIAssistant.feature.conversation.dto.response.StreamingSessionInfo;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

/**
 * 스트리밍 청크 처리 (공통 로직)
 */
@Component
@RequiredArgsConstructor
public class StreamingChunkProcessor {

    // TODO: 스트리밍 처리 로직이므로 추후 확장 될 가능성 있으므로 일단 bean 유지

    /**
     * ChatResponse를 처리 가능한 청크로 변환 (공통 로직)
     */
    public ProcessedChunk processChunk(ChatResponse chatResponse, StreamingSessionInfo sessionInfo) {
        // 메타데이터 업데이트
        ChatResponseMapper.updateStreamingInfo(sessionInfo, chatResponse);

        // 텍스트 추출 및 정제
        String rawText = ChatResponseMapper.extractRawText(chatResponse);
        String cleanText = ThinkBlockProcessor.stripThinkBlocks(rawText);

        // tool_calls 추출
        List<Map<String, Object>> toolCalls = ChatResponseMapper.extractToolCalls(chatResponse);

        return new ProcessedChunk(cleanText, sessionInfo, toolCalls);
    }
}
