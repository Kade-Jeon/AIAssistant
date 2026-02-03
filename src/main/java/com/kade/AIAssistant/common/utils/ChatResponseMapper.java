package com.kade.AIAssistant.common.utils;

import com.kade.AIAssistant.feature.conversation.dto.response.StreamingSessionInfo;
import com.kade.AIAssistant.feature.conversation.dto.response.UsageInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * ChatResponse 응답 변환 및 메타데이터 추출 유틸리티
 */
@Slf4j
public final class ChatResponseMapper {

    private ChatResponseMapper() {
        // 유틸 클래스 - 인스턴스 생성 금지
    }

    /**
     * ChatResponse에서 텍스트 추출
     */
    public static String extractRawText(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResult() == null) {
            return "";
        }

        AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
        if (assistantMessage == null) {
            return "";
        }

        String rawContent = assistantMessage.getText();
        if (rawContent == null || rawContent.isEmpty()) {
            return "";
        }

        return rawContent;
    }

    /**
     * ChatResponse에서 Usage 정보 추출
     */
    public static UsageInfo getUsageInfo(ChatResponse response) {
        try {
            if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
                return null;
            }
            Usage usage = response.getMetadata().getUsage();
            Integer prompt = usage.getPromptTokens();
            Integer total = usage.getTotalTokens();
            Integer completion = usage.getCompletionTokens();
            // completionTokens이 없으면 직접 계산
            if (completion == null && prompt != null && total != null) {
                completion = total - prompt;
            }
            return UsageInfo.of(prompt, completion, total);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * ChatResponse에서 tool_calls 추출 (OpenAI 호환 형식으로 변환)
     */
    public static List<Map<String, Object>> extractToolCalls(ChatResponse chatResponse) {
        try {
            if (chatResponse == null ||
                    chatResponse.getResult() == null ||
                    chatResponse.getResult().getOutput() == null) {
                return null;
            }

            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            List<AssistantMessage.ToolCall> toolCallsList = assistantMessage.getToolCalls();

            if (toolCallsList == null || toolCallsList.isEmpty()) {
                return null;
            }

            List<Map<String, Object>> toolCalls = new ArrayList<>(toolCallsList.size());

            for (int index = 0; index < toolCallsList.size(); index++) {
                AssistantMessage.ToolCall toolCall = toolCallsList.get(index);

                Map<String, Object> tc = new HashMap<>();
                tc.put("index", index);
                tc.put("id", toolCall.id());
                tc.put("type", toolCall.type());

                Map<String, Object> function = new HashMap<>();
                function.put("name", toolCall.name());
                function.put("arguments", toolCall.arguments());
                tc.put("function", function);

                toolCalls.add(tc);
            }

            return toolCalls;
        } catch (Exception e) {
            log.debug("tool_calls 추출 중 예외: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 스트리밍 세션 정보 업데이트 ChatResponse의 메타데이터에서 토큰 정보와 성능 정보를 추출하여 누적합니다.
     */
    public static void updateStreamingInfo(StreamingSessionInfo info, ChatResponse chatResponse) {
        try {
            if (chatResponse == null) {
                info.setChunkCount(info.getChunkCount() + 1);
                return;
            }

            // 청크 수 증가
            info.setChunkCount(info.getChunkCount() + 1);

            // 메타데이터가 있는 경우에만 처리 (Ollama에서는 마지막 청크에만 메타데이터 제공)
            if (chatResponse.getMetadata() != null) {
                var metadata = chatResponse.getMetadata();

                // Spring AI 표준 메서드 활용
                if (metadata.getUsage() != null) {
                    var usage = metadata.getUsage();

                    // 토큰 정보
                    Integer promptTokens = usage.getPromptTokens();
                    if (promptTokens != null && promptTokens > 0) {
                        info.setPromptTokens(promptTokens);
                    }

                    Integer totalTokens = usage.getTotalTokens();
                    if (totalTokens != null && totalTokens > 0) {
                        info.setTotalTokens(totalTokens);
                        // completion = total - prompt
                        if (promptTokens != null) {
                            info.setCompletionTokens((totalTokens - promptTokens));
                        }
                    }
                }

                // 로그로 메타데이터 확인
                log.debug("스트리밍 메타데이터: {}", metadata);
            }

            String finishReason = chatResponse.getResult().getMetadata().getFinishReason();
            info.setFinishReason(finishReason);

        } catch (Exception e) {
            log.warn("스트리밍 정보 업데이트 중 오류: {}", e.getMessage());
        }
    }
}
