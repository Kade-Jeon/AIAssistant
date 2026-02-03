package com.kade.AIAssistant.feature.conversation.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * 대화 목록 조회 API 응답용 메시지 DTO.
 */
public record ConversationMessageDto(
        String role,   // user, assistant, system, tool
        String content,
        Instant timestamp,
        List<AttachmentDto> attachments  // 첨부파일 목록 (없으면 null 또는 빈 리스트)
) {
    public ConversationMessageDto(String role, String content, Instant timestamp) {
        this(role, content, timestamp, null);
    }
}
