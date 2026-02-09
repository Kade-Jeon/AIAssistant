package com.kade.AIAssistant.feature.conversation.service.idempotency;

import com.kade.AIAssistant.feature.conversation.dto.request.AssistantRequest;
import com.kade.AIAssistant.feature.conversation.service.IdempotencyState;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 상태가 없는 경우(최초 요청) 처리 핸들러.
 * 새로운 대화를 시작합니다.
 */
@Slf4j
@Component
public class NoStateHandler implements IdempotencyStateHandler {

    @Override
    public boolean canHandle(Optional<IdempotencyState> state) {
        return state.isEmpty();
    }

    @Override
    public IdempotencyResolutionResult handle(
            String userId,
            String idempotencyKey,
            Optional<IdempotencyState> state,
            AssistantRequest request,
            SseEmitter emitter
    ) {
        String conversationId = resolveConversationId(request);
        log.info("새 요청 처리 시작 - userId: {}, idempotencyKey: {}, conversationId: {}",
                userId, idempotencyKey, conversationId);

        return new IdempotencyResolutionResult(conversationId, false, false);
    }

    private String resolveConversationId(AssistantRequest request) {
        return StringUtils.hasText(request.conversationId())
                ? request.conversationId()
                : UUID.randomUUID().toString();
    }
}
