package com.kade.AIAssistant.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kade.AIAssistant.domain.response.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * /api/v1/ai/conv 요청에 대해 USER-ID 헤더가 비어 있으면 400 응답으로 바로 끊는다.
 * <p>컨트롤러에서 반복하던 USER-ID 검증을 앞단 필터로 모은다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class UserIdRequiredFilter extends OncePerRequestFilter {

    private static final String CONVERSATION_API_PREFIX = "/api/v1/ai/conv";
    private static final String HEADER_USER_ID = "USER-ID";
    private static final String MESSAGE_USER_ID_REQUIRED = "USER-ID 헤더를 제공해주세요.";

    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith(CONVERSATION_API_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String userId = request.getHeader(HEADER_USER_ID);
        if (!StringUtils.hasText(userId)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json;charset=UTF-8");
            ErrorResponse body = ErrorResponse.of(
                    "INVALID_REQUEST",
                    MESSAGE_USER_ID_REQUIRED,
                    null,
                    request.getRequestURI(),
                    HttpServletResponse.SC_BAD_REQUEST
            );
            response.getWriter().write(objectMapper.writeValueAsString(body));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
