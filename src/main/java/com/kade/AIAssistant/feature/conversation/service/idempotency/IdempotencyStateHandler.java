package com.kade.AIAssistant.feature.conversation.service.idempotency;

import com.kade.AIAssistant.feature.conversation.dto.request.AssistantRequest;
import com.kade.AIAssistant.feature.conversation.service.IdempotencyState;
import java.util.Optional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Idempotency 상태별 처리 전략 인터페이스.
 * Strategy Pattern을 적용하여 각 상태(COMPLETED, IN_PROGRESS, FAILED, NO_STATE)에 따른
 * 처리 로직을 별도 클래스로 분리합니다.
 */
public interface IdempotencyStateHandler {

    /**
     * 이 핸들러가 처리할 수 있는 상태인지 확인
     *
     * @param state Idempotency 상태 (null인 경우 상태가 없음을 의미)
     * @return 처리 가능 여부
     */
    boolean canHandle(Optional<IdempotencyState> state);

    /**
     * Idempotency 상태에 따른 처리 수행
     *
     * @param userId         사용자 ID
     * @param idempotencyKey Idempotency-Key
     * @param state          현재 상태
     * @param request        요청 정보
     * @param emitter        SSE 에미터
     * @return 처리 결과
     */
    IdempotencyResolutionResult handle(
            String userId,
            String idempotencyKey,
            Optional<IdempotencyState> state,
            AssistantRequest request,
            SseEmitter emitter
    );
}
