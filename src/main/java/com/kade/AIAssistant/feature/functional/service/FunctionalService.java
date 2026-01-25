package com.kade.AIAssistant.feature.functional.service;

import com.kade.AIAssistant.domain.reqeust.AssistantRequest;
import com.kade.AIAssistant.domain.response.StreamingSessionInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

/**
 * AI 채팅 서비스 (핵심 비즈니스 로직) - SOLID 원칙 준수: 각 책임을 전문 컴포넌트에 위임
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FunctionalService {

    @Value("${spring.ai.ollama.chat.model:default}")
    private String MODEL_NAME;

    private final StreamingService streamingService;
    private final ModelExecuteService modelExecuteService;


    /**
     * [SSE 스트리밍] AI 채팅 응답 생성
     *
     * @param request 사용자 요청
     * @param emitter SSE Emitter
     */
    public void streamToSse(AssistantRequest request, SseEmitter emitter) {
        log.info("SSE 스트리밍 시작 - 질문: {}", request.question());
        // AI 모델 스트리밍 호출
        Flux<ChatResponse> stream = modelExecuteService.stream(request);

        // 세션 정보 초기화
        StreamingSessionInfo sessionInfo = new StreamingSessionInfo();
        sessionInfo.setModel(MODEL_NAME);

        // SSE 스트리밍 처리 (위임)
        streamingService.streamToSse(stream, emitter, sessionInfo);
    }
}
