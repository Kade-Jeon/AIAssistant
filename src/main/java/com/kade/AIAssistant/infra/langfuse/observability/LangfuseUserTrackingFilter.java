package com.kade.AIAssistant.infra.langfuse.observability;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Langfuse trace-level user/session/tenant attributes 설정 필터.
 * <p>
 * - Gateway에서 내려주는 header 값을 우선 사용합니다. - Trace-level attributes는 가급적 루트 HTTP 서버 스팬(Span.current())에 설정합니다. - 또한
 * Baggage에 저장하여 이후 생성되는 자식 Span(비동기 등)에도 속성이 전파되도록 합니다. (LangfuseBaggageSpanProcessor와 함께 동작)
 * <p>
 * 참고: - https://langfuse.com/integrations/native/opentelemetry#trace-level-attributes -
 * https://langfuse.com/integrations/frameworks/spring-ai
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE) // controller 전에 실행되지만, 가능한 한 늦게 실행(스팬 생성 이후)을 기대
public class LangfuseUserTrackingFilter extends OncePerRequestFilter {

    private static final String HDR_USER_ID = "USER-ID";
    private static final String HDR_TENANT = "TENANT";
    private static final String HDR_SESSION_ID = "X-Session-Id";
    private static final String HDR_CONVERSATION_ID = "X-Conversation-Id";

    private final String environmentName;

    public LangfuseUserTrackingFilter(
            @Value("${spring.profiles.active:default}") String configuredEnvironment
    ) {
        this.environmentName = StringUtils.hasText(configuredEnvironment) ? configuredEnvironment : "default";
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // 1. 헤더 값 추출
        String userId = firstNonBlank(
                request.getHeader(HDR_USER_ID)
        );
        String sessionId = firstNonBlank(
                request.getHeader(HDR_SESSION_ID),
                request.getHeader(HDR_CONVERSATION_ID)
        );
        String tenant = firstNonBlank(
                request.getHeader(HDR_TENANT)
        );

        //TODO : 추후 변경
        // 2. 값 결정 (기존 하드코딩 로직 유지 및 헤더 값 적용)
        String finalUserId = "gbdc_co_kr:208"; // 기존 하드코딩 값
        String finalSessionId = StringUtils.hasText(sessionId) ? sessionId : "thisISsession8";
        String finalEnvironment = "dev";
        //String finalEnvironment = StringUtils.hasText(environmentName) ? environmentName : null;

        // Baggage 설정 (Context Propagation용 - LangfuseBaggageSpanProcessor에서 사용)
        BaggageBuilder baggageBuilder = Baggage.builder();
        baggageBuilder.put("langfuse.user.id", finalUserId);
        baggageBuilder.put("langfuse.session.id", finalSessionId);

        if (StringUtils.hasText(tenant)) {
            baggageBuilder.put("langfuse.trace.tags", "tenant=" + tenant);
            baggageBuilder.put("langfuse.tenant.id", tenant);
        } else {
            baggageBuilder.put("langfuse.trace.tags", "gbdc_co_kr");
        }

        if (finalEnvironment != null) {
            baggageBuilder.put("langfuse.environment", finalEnvironment);
        }

        Baggage baggage = baggageBuilder.build();

        // Baggage Context Scope 내에서 필터 체인 실행
        try (Scope scope = Context.current().with(baggage).makeCurrent()) {
            filterChain.doFilter(request, response);
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (StringUtils.hasText(v)) {
                return v;
            }
        }
        return null;
    }
}
