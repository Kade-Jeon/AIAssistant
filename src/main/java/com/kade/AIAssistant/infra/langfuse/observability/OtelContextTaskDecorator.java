package com.kade.AIAssistant.infra.langfuse.observability;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.springframework.core.task.TaskDecorator;

/**
 * @Async 실행 시 OpenTelemetry Context(현재 Span 포함)를 스레드풀 스레드로 전파합니다.
 * <p>
 * 이게 없으면 /async 요청에서 모델 호출 span이 HTTP 서버 span의 자식으로 연결되지 않아 Langfuse에서 루트가 `http post /api/.../async`가 아니라 모델 span(예:
 * `chat gpt-oss:20b`)으로 보일 수 있습니다.
 */
public class OtelContextTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Context captured = Context.current();
        return () -> {
            try (Scope scope = captured.makeCurrent()) {
                runnable.run();
            }
        };
    }
}
