package com.kade.AIAssistant.domain.response;

import java.time.Instant;

/**
 * 대화 목록 조회 API 응답용 메시지 DTO.
 */
public record ConversationMessageDto(
        String role,   // user, assistant, system, tool
        String content,
        Instant timestamp
) {
}
