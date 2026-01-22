package com.kade.AIAssistant.config;

import com.kade.AIAssistant.infra.langfuse.observability.LangfuseBaggageSpanProcessor;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenTelemetry Tracer Bean 설정.
 * <p>
 * Langfuse(Spring AI + OTel) 예제에서 사용하는 tracer는 OpenTelemetry의 Tracer 입니다. 참고:
 * https://langfuse.com/integrations/frameworks/spring-ai
 */
@Configuration
public class OtelTracerConfig {

    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer("ai-api");
    }

    /**
     * Baggage를 Span Attribute로 복사하는 프로세서 등록. AutoConfigurationCustomizerProvider를 사용하여 OpenTelemetry SDK 초기화 시점에 명시적으로
     * 프로세서를 추가합니다. 이는 @Bean이나 @Component 등록 시 발생할 수 있는 초기화 순서 문제를 방지합니다.
     */
    @Bean
    public AutoConfigurationCustomizerProvider otelCustomizer() {
        return p -> p.addTracerProviderCustomizer((builder, config) -> {
            builder.addSpanProcessor(new LangfuseBaggageSpanProcessor());
            return builder;
        });
    }
}
