package com.kade.AIAssistant.feature.conversation.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kade.AIAssistant.common.exceptions.customs.InvalidRequestException;
import com.kade.AIAssistant.domain.reqeust.AssistantRequest;
import com.kade.AIAssistant.feature.conversation.service.ConversationService;
import com.kade.AIAssistant.feature.conversation.service.RagService;
import jakarta.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 를 기능형으로 활용할 수 있는 컨트롤러 입니다.
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/ai/conv")
public class ConversationController {

    private final ConversationService conversationService;
    private final RagService ragService;
    private final ObjectMapper objectMapper;

    @Value("${app.sse.timeout:300000}")
    private long SSE_TIMEOUT;

    // 현재는 강제로 헤더에 USER-ID를 넣어 처리하지만, 인증서버가 있다면 인증 서버를 통해 전달받음.
    @PostMapping(value = "", consumes = MediaType.APPLICATION_JSON_VALUE)
    public SseEmitter conversationStream(
            @RequestBody @Valid AssistantRequest request,
            @RequestHeader(value = "USER-ID", required = false) String userIdHeader,
            HttpServletRequest httpRequest
    ) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new InvalidRequestException("USER-ID 헤더를 제공해주세요.");
        }

        log.info("""
                        [SSE 스트리밍 채팅 요청] 프롬프트 타입: {}
                        질문: {}
                        userId: {}
                        """,
                request.promptType(), request.question(), userIdHeader);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        try {
            emitter.send(SseEmitter.event().name("open").data("connected"));
            conversationService.streamToSse(userIdHeader, request, emitter);
        } catch (Exception e) {
            log.error("SSE 스트리밍 초기화 실패", e);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    /** 특정 유저의 모든 대화 목록(conversationId, subject) 반환. USER-ID 헤더 필수. */
    @GetMapping("")
    public ResponseEntity<?> getConversations(
            @RequestHeader(value = "USER-ID", required = false) String userIdHeader
    ) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new InvalidRequestException("USER-ID 헤더를 제공해주세요.");
        }
        return ResponseEntity.ok(conversationService.getConversations(userIdHeader));
    }

    @PostMapping(value = "", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SseEmitter conversationStreamWithFile(
            @RequestPart("file") MultipartFile file,
            @RequestPart("request") String requestJson,
            @RequestHeader(value = "USER-ID", required = false) String userIdHeader,
            HttpServletRequest httpRequest
    ) {
        log.info("[SSE 스트리밍 파일 첨부 요청] 파일명: {}", file.getOriginalFilename());

        // 검증 완료 후 emitter 생성 (실패 시 InvalidRequestException → HTTP 400)
        AssistantRequest request;
        try {
            request = objectMapper.readValue(requestJson, AssistantRequest.class);
        } catch (JsonProcessingException e) {
            log.error("요청 JSON 파싱 실패", e);
            throw new InvalidRequestException("요청 데이터 형식이 올바르지 않습니다: " + e.getMessage());
        }

        if (!StringUtils.hasText(userIdHeader)) {
            throw new InvalidRequestException("USER-ID 헤더를 제공해주세요.");
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        try {
            String fileContent = ragService.extractText(file);
            log.info("파일 텍스트 추출 완료: 파일명={}, 추출된 텍스트 길이={}",
                    file.getOriginalFilename(), fileContent.length());

            String combinedQuestion = String.format(
                    "다음 첨부파일(문서) 내용:\n\n%s\n\n사용자 요청: %s",
                    fileContent,
                    request.question()
            );

            AssistantRequest fileRequest = new AssistantRequest(
                    request.promptType(),
                    combinedQuestion,
                    request.language(),
                    request.conversationId(),
                    request.subject()
            );

            emitter.send(SseEmitter.event().name("open").data("connected"));
            conversationService.streamToSse(userIdHeader, fileRequest, emitter);
        } catch (Exception e) {
            log.error("파일 처리 실패", e);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<?> getConversation(
            @PathVariable String conversationId,
            @RequestHeader(value = "USER-ID", required = false) String userIdHeader,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new InvalidRequestException("USER-ID 헤더를 제공해주세요.");
        }
        return ResponseEntity.ok(conversationService.getConversation(userIdHeader, conversationId, limit));
    }
}
