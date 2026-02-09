package com.kade.AIAssistant.feature.conversation.service.idempotency;

import com.kade.AIAssistant.feature.conversation.dto.request.AssistantRequest;
import com.kade.AIAssistant.feature.conversation.service.IdempotencyState;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * COMPLETED 상태 처리 핸들러.
 * 이미 완료된 요청에 대해 already_completed 이벤트를 전송하고 스트림을 종료합니다.
 */
@Slf4j
@Component
public class CompletedStateHandler implements IdempotencyStateHandler {

    @Override
    public boolean canHandle(Optional<IdempotencyState> state) {
        return state.isPresent() && IdempotencyState.COMPLETED.equals(state.get().getStatus());
    }

    @Override
    public IdempotencyResolutionResult handle(
            String userId,
            String idempotencyKey,
            Optional<IdempotencyState> state,
            AssistantRequest request,
            SseEmitter emitter
    ) {
        IdempotencyState completedState = state.orElseThrow();
        String conversationId = completedState.getConversationId();

        try {
            emitter.send(SseEmitter.event()
                    .name("already_completed")
                    .data(Map.of("conversationId", conversationId)));
        } catch (IOException e) {
            log.warn("already_completed 이벤트 전송 실패 - idempotencyKey: {}", idempotencyKey, e);
        }
        emitter.complete();

        log.info("이미 완료된 요청 - userId: {}, idempotencyKey: {}, conversationId: {}",
                userId, idempotencyKey, conversationId);

        return new IdempotencyResolutionResult(conversationId, true, true);
    }
}
