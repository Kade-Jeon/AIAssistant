package com.kade.AIAssistant.feature.functional.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 를 기능형으로 활용할 수 있는 컨트롤러 입니다.
 */
@Slf4j
@RequiredArgsConstructor
@RestController("/api/v1/ai/func")
public class FunctionalContorller {

    private final long SSE_TIMEOUT = 300000L;

}
