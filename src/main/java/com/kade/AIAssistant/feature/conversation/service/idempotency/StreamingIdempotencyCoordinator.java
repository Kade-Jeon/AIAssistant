package com.kade.AIAssistant.feature.conversation.service.idempotency;

import com.kade.AIAssistant.feature.conversation.dto.request.AssistantRequest;
import com.kade.AIAssistant.feature.conversation.service.IdempotencyService;
import com.kade.AIAssistant.feature.conversation.service.IdempotencyState;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * Idempotency 처리를 조정하는 코디네이터.
 * Strategy Pattern을 사용하여 상태별 처리를 위임하고,
 * Idempotency 관련 모든 로직을 캡슐화합니다.
 *
 * 책임:
 * - Idempotency 키 검증
 * - 상태별 적절한 Handler 선택 및 위임
 * - Claim/Complete/Failed 처리
 * - 에러 핸들링 설정
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StreamingIdempotencyCoordinator {

    private final IdempotencyService idempotencyService;
    private final List<IdempotencyStateHandler> handlers;

    /**
     * Idempotency-Key가 있는 경우 상태를 확인하고 적절한 처리를 수행합니다.
     *
     * @param userId         사용자 ID
     * @param idempotencyKey Idempotency-Key
     * @param request        요청 정보
     * @param emitter        SSE 에미터
     * @return 처리 결과
     */
    public IdempotencyResolutionResult resolve(
            String userId,
            String idempotencyKey,
            AssistantRequest request,
            SseEmitter emitter
    ) {
        if (!StringUtils.hasText(idempotencyKey)) {
            // Idempotency-Key가 없는 경우 - 새 대화 시작
            String conversationId = resolveConversationId(request);
            return new IdempotencyResolutionResult(conversationId, false, false);
        }

        // Idempotency 상태 조회
        Optional<IdempotencyState> state = idempotencyService.get(userId, idempotencyKey);

        // 적절한 핸들러 선택 및 처리 위임
        IdempotencyStateHandler handler = selectHandler(state);
        return handler.handle(userId, idempotencyKey, state, request, emitter);
    }

    /**
     * Idempotency claim 처리.
     * 사용자 메시지 저장 후 호출되어 요청을 IN_PROGRESS 상태로 등록합니다.
     *
     * @param userId         사용자 ID
     * @param idempotencyKey Idempotency-Key
     * @param conversationId 대화 ID
     * @param userMessageId  저장된 사용자 메시지 ID
     * @return claim 성공 여부
     */
    public boolean claim(String userId, String idempotencyKey, String conversationId, UUID userMessageId) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return true;
        }

        boolean claimed = idempotencyService.claim(userId, idempotencyKey, conversationId, userMessageId);
        if (!claimed) {
            log.warn("Idempotency claim 실패 - userId: {}, idempotencyKey: {}", userId, idempotencyKey);
        }
        return claimed;
    }

    /**
     * 스트림에 에러 핸들링 설정 추가.
     * 에러 발생 시 Idempotency 상태를 FAILED로 변경하고 retry lock을 해제합니다.
     *
     * @param stream         원본 스트림
     * @param userId         사용자 ID
     * @param idempotencyKey Idempotency-Key
     * @return 에러 핸들링이 추가된 스트림
     */
    public Flux<ChatResponse> attachErrorHandler(
            Flux<ChatResponse> stream,
            String userId,
            String idempotencyKey
    ) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return stream;
        }

        return stream.doOnError(e -> {
            log.error("스트리밍 에러 발생 - userId: {}, idempotencyKey: {}", userId, idempotencyKey, e);
            idempotencyService.markFailed(userId, idempotencyKey);
            idempotencyService.releaseRetryLock(userId, idempotencyKey);
        });
    }

    /**
     * 스트리밍 완료 시 Idempotency 상태를 COMPLETED로 변경하고 retry lock을 해제합니다.
     *
     * @param userId         사용자 ID
     * @param idempotencyKey Idempotency-Key
     */
    public void markCompleted(String userId, String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return;
        }

        idempotencyService.markCompleted(userId, idempotencyKey);
        idempotencyService.releaseRetryLock(userId, idempotencyKey);
        log.debug("Idempotency 완료 처리 - userId: {}, idempotencyKey: {}", userId, idempotencyKey);
    }

    /**
     * 상태에 맞는 핸들러를 선택합니다.
     */
    private IdempotencyStateHandler selectHandler(Optional<IdempotencyState> state) {
        return handlers.stream()
                .filter(handler -> handler.canHandle(state))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "적절한 IdempotencyStateHandler를 찾을 수 없습니다. state: " +
                                state.map(IdempotencyState::getStatus).orElse("NO_STATE")
                ));
    }

    private String resolveConversationId(AssistantRequest request) {
        return StringUtils.hasText(request.conversationId())
                ? request.conversationId()
                : UUID.randomUUID().toString();
    }
}
