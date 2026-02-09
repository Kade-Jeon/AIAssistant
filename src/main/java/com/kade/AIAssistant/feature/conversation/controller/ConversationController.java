package com.kade.AIAssistant.feature.conversation.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kade.AIAssistant.common.exceptions.customs.InvalidRequestException;
import com.kade.AIAssistant.feature.conversation.dto.request.AssistantRequest;
import com.kade.AIAssistant.feature.conversation.dto.request.ChangeSubjectRequest;
import com.kade.AIAssistant.feature.conversation.service.ConversationService;
import com.kade.AIAssistant.feature.conversation.service.DocumentService;
import jakarta.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 를 기능형으로 활용할 수 있는 컨트롤러 입니다. USER-ID 헤더 검증은 앞단 필터(common.filters.UserIdRequiredFilter)에서 수행한다.
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/ai/conv")
public class ConversationController {

    private final ConversationService conversationService;
    private final DocumentService ragService;
    private final ObjectMapper objectMapper;

    @Value("${app.sse.timeout:300000}")
    private long SSE_TIMEOUT;

    @PostMapping(value = "", consumes = MediaType.APPLICATION_JSON_VALUE)
    public SseEmitter conversationStream(
            @RequestBody @Valid AssistantRequest request,
            @RequestHeader(value = "USER-ID") String userIdHeader,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest httpRequest
    ) {
        log.info("""
                        [SSE 스트리밍 채팅 요청] 프롬프트 타입: {}
                        질문: {}
                        userId: {}
                        """,
                request.promptType(), request.question(), userIdHeader);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        try {
            emitter.send(SseEmitter.event().name("open").data("connected"));
            conversationService.streamToSse(userIdHeader, request, emitter, idempotencyKey);
        } catch (Exception e) {
            log.error("SSE 스트리밍 초기화 실패", e);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    /**
     * 특정 유저의 모든 대화 목록(conversationId, subject) 반환. USER-ID 헤더 필수.
     */
    @GetMapping("")
    public ResponseEntity<?> getConversations(
            @RequestHeader(value = "USER-ID") String userIdHeader
    ) {
        return ResponseEntity.ok(conversationService.getConversations(userIdHeader));
    }

    @PostMapping(value = "", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SseEmitter conversationStreamWithFile(
            @RequestPart("file") MultipartFile file,
            @RequestPart("request") String requestJson,
            @RequestHeader(value = "USER-ID") String userIdHeader,
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
                    request.conversationId(),
                    request.subject()
            );

            emitter.send(SseEmitter.event().name("open").data("connected"));

            // 첨부파일 메타데이터 저장을 위한 정보 준비
            String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
            String mimeType = file.getContentType();
            Long fileSize = file.getSize();

            // conversationId는 streamToSseWithUserMessageId에서 결정되므로, AtomicReference로 전달
            java.util.concurrent.atomic.AtomicReference<String> conversationIdRef =
                    new java.util.concurrent.atomic.AtomicReference<>();

            // 스트리밍 완료 후 첨부파일 메타데이터 저장 (userMessageId 직접 사용)
            java.util.function.Consumer<java.util.UUID> saveAttachmentCallback = (userMessageId) -> {
                String conversationId = conversationIdRef.get();
                if (userMessageId != null && conversationId != null) {
                    log.info("스트리밍 완료 후 첨부파일 메타데이터 저장 시작 - conversationId: {}, userMessageId: {}",
                            conversationId, userMessageId);
                    conversationService.saveAttachmentMetadata(
                            conversationId,
                            userMessageId,
                            filename,
                            mimeType,
                            fileSize
                    );
                } else {
                    log.warn(
                            "스트리밍 완료 콜백 실행 시 conversationId 또는 userMessageId가 null입니다 - conversationId: {}, userMessageId: {}",
                            conversationId, userMessageId);
                }
            };

            // streamToSseWithUserMessageId 호출 (userMessageId를 콜백으로 전달)
            String conversationId = conversationService.streamToSseWithUserMessageId(
                    userIdHeader, fileRequest, emitter, saveAttachmentCallback);

            // conversationId 설정 (비동기 콜백에서 사용)
            conversationIdRef.set(conversationId);
        } catch (Exception e) {
            log.error("파일 처리 실패", e);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<?> getConversation(
            @PathVariable String conversationId,
            @RequestHeader(value = "USER-ID") String userIdHeader,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "beforeTimestamp", required = false) java.time.Instant beforeTimestamp
    ) {
        return ResponseEntity.ok(conversationService.getConversation(
                userIdHeader, conversationId, limit, beforeTimestamp));
    }

    @DeleteMapping("/{conversationId}")
    public ResponseEntity<?> deleteConversation(
            @PathVariable String conversationId,
            @RequestHeader(value = "USER-ID") String userIdHeader
    ) {
        boolean deleted = conversationService.deleteConversation(userIdHeader, conversationId);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @PatchMapping("/{conversationId}")
    public ResponseEntity<Void> changeSubject(
            @PathVariable String conversationId,
            @RequestHeader(value = "USER-ID") String userIdHeader,
            @RequestBody @Valid ChangeSubjectRequest request
    ) {
        conversationService.changeSubject(userIdHeader, conversationId, request.subject());
        return ResponseEntity.noContent().build();
    }
}
