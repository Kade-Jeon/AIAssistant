package com.kade.AIAssistant.feature.conversation.service.idempotency;

import com.kade.AIAssistant.feature.conversation.dto.request.AssistantRequest;
import com.kade.AIAssistant.feature.conversation.service.IdempotencyService;
import com.kade.AIAssistant.feature.conversation.service.IdempotencyState;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * FAILED 상태 처리 핸들러.
 * 실패한 요청에 대해 재시도를 시도합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FailedStateHandler implements IdempotencyStateHandler {

    private final IdempotencyService idempotencyService;

    @Override
    public boolean canHandle(Optional<IdempotencyState> state) {
        return state.isPresent() && IdempotencyState.FAILED.equals(state.get().getStatus());
    }

    @Override
    public IdempotencyResolutionResult handle(
            String userId,
            String idempotencyKey,
            Optional<IdempotencyState> state,
            AssistantRequest request,
            SseEmitter emitter
    ) {
        log.info("실패한 요청 재시도 시도 - userId: {}, idempotencyKey: {}", userId, idempotencyKey);

        IdempotencyState retryState = idempotencyService.tryStartRetry(userId, idempotencyKey);

        if (retryState != null) {
            // 재시도 성공 - 기존 conversationId 사용, 사용자 메시지 저장 스킵
            String conversationId = retryState.getConversationId();
            log.info("재시도 성공 - conversationId: {}, userMessageId: {}",
                    conversationId, retryState.getUserMessageId());
            return new IdempotencyResolutionResult(conversationId, true, false);
        } else {
            // 재시도 실패 - 새로운 conversationId 생성, 사용자 메시지 저장 진행
            String conversationId = resolveConversationId(request);
            log.info("재시도 실패, 새 대화 생성 - conversationId: {}", conversationId);
            return new IdempotencyResolutionResult(conversationId, false, false);
        }
    }

    private String resolveConversationId(AssistantRequest request) {
        return StringUtils.hasText(request.conversationId())
                ? request.conversationId()
                : UUID.randomUUID().toString();
    }
}
