package com.kade.AIAssistant.common.filters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kade.AIAssistant.domain.response.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * /api/v1/ai/conv, /api/v1/ai/pref 요청에 대해 USER-ID 헤더가 비어 있으면 400 응답으로 바로 끊는다.
 * <p>컨트롤러에서 반복하던 USER-ID 검증을 앞단 필터로 모은다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class UserIdRequiredFilter extends OncePerRequestFilter {

    private static final String HEADER_USER_ID = "USER-ID";
    private static final String MESSAGE_USER_ID_REQUIRED = "USER-ID 헤더를 제공해주세요.";
    
    /**
     * USER-ID 헤더 검증이 필요한 API 경로 목록
     */
    private static final List<String> REQUIRED_USER_ID_PATHS = List.of(
            "/api/v1/ai/conv",
            "/api/v1/ai/pref"
    );

    private final ObjectMapper objectMapper;

    /**
     * 필터링 대상 경로가 아니면 필터를 적용하지 않음
     * 
     * @param request HTTP 요청
     * @return 필터링 대상 경로면 false (필터 적용), 아니면 true (필터 적용 안 함)
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return true;
        }
        // 필터링 대상 경로 중 하나라도 매치되면 필터 적용 (false 반환)
        return !REQUIRED_USER_ID_PATHS.stream()
                .anyMatch(path::startsWith);
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
