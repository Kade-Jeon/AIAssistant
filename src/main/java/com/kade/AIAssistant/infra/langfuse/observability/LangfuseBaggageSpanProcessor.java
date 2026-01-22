package com.kade.AIAssistant.infra.langfuse.observability;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import lombok.extern.slf4j.Slf4j;

/**
 * OpenTelemetry Baggage를 Span Attribute로 자동 변환하는 프로세서.
 * <p>
 * Langfuse는 `langfuse.` 접두사가 붙은 속성을 사용하여 trace/generation을 그룹화합니다. 비동기 호출 등에서 Span이 새로 생성될 때, Context에 저장된
 * Baggage(user_id 등)를 읽어서 Span의 Attribute로 자동으로 복사해줍니다.
 */
@Slf4j
public class LangfuseBaggageSpanProcessor implements SpanProcessor {

    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
        Baggage baggage = Baggage.fromContext(parentContext);
        if (log.isDebugEnabled()) {
            log.debug("LangfuseBaggageSpanProcessor.onStart: Span={}, Baggage={}", span, baggage);
        }

        baggage.forEach((key, baggageEntry) -> {
            // "langfuse." 로 시작하는 모든 Baggage Entry를 Span Attribute로 추가
            if (key.startsWith("langfuse.")) {
                span.setAttribute(key, baggageEntry.getValue());
            }
        });
    }

    @Override
    public boolean isStartRequired() {
        return true;
    }

    @Override
    public void onEnd(ReadableSpan span) {

    }

    @Override
    public boolean isEndRequired() {
        return false;
    }
}
