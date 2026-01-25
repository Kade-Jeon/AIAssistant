package com.kade.AIAssistant.feature.functional.controller;

import com.kade.AIAssistant.domain.reqeust.AssistantRequest;
import com.kade.AIAssistant.feature.functional.service.FunctionalService;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 를 기능형으로 활용할 수 있는 컨트롤러 입니다.
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/ai/func")
public class FunctionalController {

    private final long SSE_TIMEOUT = 300000L;

    private final FunctionalService functionalService;

    @PostMapping("")
    public SseEmitter functionalStream(@RequestBody @Valid AssistantRequest request) {
        log.info("""
                        [SSE 스트리밍 채팅 요청] 프롬프트 타입: {}
                        질문: {},\s
                        """,
                request.promptType(), request.question());
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        try {
            emitter.send(SseEmitter.event().name("open").data("connected"));
            functionalService.streamToSse(request, emitter);
        } catch (Exception e) {
            log.error("SSE 스트리밍 초기화 실패", e);
            emitter.completeWithError(e);
        }

        return emitter;
    }
}
