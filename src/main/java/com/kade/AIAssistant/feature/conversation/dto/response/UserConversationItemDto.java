package com.kade.AIAssistant.feature.conversation.dto.response;

/**
 * 유저의 대화 목록 한 건 (conversationId, subject).
 */
public record UserConversationItemDto(
        String conversationId,
        String subject
) {
}
