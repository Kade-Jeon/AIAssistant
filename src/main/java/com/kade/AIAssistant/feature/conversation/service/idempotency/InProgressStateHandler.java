package com.kade.AIAssistant.feature.conversation.service.idempotency;

import com.kade.AIAssistant.common.exceptions.customs.IdempotencyConflictException;
import com.kade.AIAssistant.feature.conversation.dto.request.AssistantRequest;
import com.kade.AIAssistant.feature.conversation.service.IdempotencyState;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * IN_PROGRESS 상태 처리 핸들러.
 * 동일한 키로 요청이 이미 처리 중인 경우 충돌 예외를 발생시킵니다.
 */
@Slf4j
@Component
public class InProgressStateHandler implements IdempotencyStateHandler {

    @Override
    public boolean canHandle(Optional<IdempotencyState> state) {
        return state.isPresent() && IdempotencyState.IN_PROGRESS.equals(state.get().getStatus());
    }

    @Override
    public IdempotencyResolutionResult handle(
            String userId,
            String idempotencyKey,
            Optional<IdempotencyState> state,
            AssistantRequest request,
            SseEmitter emitter
    ) {
        log.warn("동일한 Idempotency-Key로 요청이 이미 처리 중 - userId: {}, idempotencyKey: {}",
                userId, idempotencyKey);

        throw new IdempotencyConflictException(
                "동일한 Idempotency-Key로 요청이 이미 처리 중입니다.",
                IdempotencyConflictException.CODE_REQUEST_IN_PROGRESS
        );
    }
}
