package com.kade.AIAssistant.feature.conversation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kade.AIAssistant.common.utils.StreamingChunkProcessor;
import com.kade.AIAssistant.domain.response.ChatCompletionChunk;
import com.kade.AIAssistant.domain.response.ProcessedChunk;
import com.kade.AIAssistant.domain.response.StreamingSessionInfo;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;


@Slf4j
@Service
@RequiredArgsConstructor
public class StreamingService {

    private final StreamingChunkProcessor chunkProcessor;
    private final ObjectMapper objectMapper;
    private final Scheduler sseStreamingScheduler;

    /**
     * SSE 스트리밍 처리
     *
     * @param chatResponseStream AI 모델 스트리밍 응답
     * @param emitter            SSE 에미터
     * @param sessionInfo        세션 정보
     */
    public void streamToSse(
            Flux<ChatResponse> chatResponseStream,
            SseEmitter emitter,
            StreamingSessionInfo sessionInfo
    ) {
        streamToSse(chatResponseStream, emitter, sessionInfo, null);
    }

    /**
     * SSE 스트리밍 처리 (완료 콜백 포함)
     *
     * @param chatResponseStream AI 모델 스트리밍 응답
     * @param emitter            SSE 에미터
     * @param sessionInfo        세션 정보
     * @param onCompleteCallback 스트리밍 완료 시 실행할 콜백 (선택사항)
     */
    public void streamToSse(
            Flux<ChatResponse> chatResponseStream,
            SseEmitter emitter,
            StreamingSessionInfo sessionInfo,
            Runnable onCompleteCallback
    ) {
        // 연결 상태 추적 (로그 폭탄 방지)
        AtomicBoolean isConnected = new AtomicBoolean(true);

        // SseEmitter.send()는 블로킹 I/O가 될 수 있으므로 전용 Scheduler로 오프로딩
        Flux<ChatResponse> offloadedStream = chatResponseStream.publishOn(sseStreamingScheduler);

        // Disposable 저장하여 클라이언트 연결 종료 시 구독 취소 가능하게 함
        Disposable disposable = offloadedStream.subscribe(
                // onNext: 각 청크 도착 시
                chatResponse -> {
                    // 연결이 끊어졌으면 처리하지 않음
                    if (!isConnected.get()) {
                        return;
                    }

                    try {
                        // 공통 로직: 청크 처리
                        ProcessedChunk chunk = chunkProcessor.processChunk(chatResponse, sessionInfo);

                        // finish_reason 처리
                        String finishReason = sessionInfo.getFinishReason();

                        // ChatCompletionChunk DTO 생성
                        ChatCompletionChunk chunkData = ChatCompletionChunk.chunk(
                                UUID.randomUUID().toString(),
                                sessionInfo.getStartTime().toEpochSecond(java.time.ZoneOffset.UTC),
                                sessionInfo.getModel(),
                                chunk.content(),
                                chunk.toolCalls(),
                                finishReason
                        );

                        // SSE 전송 (content가 있거나 tool_calls가 있는 경우)
                        if ((chunk.content() != null && !chunk.content().isEmpty()) ||
                                (chunk.toolCalls() != null && !chunk.toolCalls().isEmpty())) {
                            emitter.send(SseEmitter.event()
                                    .data(objectMapper.writeValueAsString(chunkData))
                                    .name("chunk"));
                        }

                    } catch (Exception e) {
                        // send 실패 시 연결 끊김으로 판단 (로그는 1회만)
                        if (isConnected.compareAndSet(true, false)) {
                            log.warn("SSE 연결 끊김 감지 - 청크 전송 중단: {}", e.getMessage());
                        }
                    }
                },

                // onError: 오류 발생 시
                error -> {
                    if (!isConnected.get()) {
                        return;
                    }

                    log.error("SSE 스트리밍 중 오류 발생", error);
                    try {
                        emitter.send(SseEmitter.event()
                                .data("AI 응답 생성 중 오류가 발생했습니다.")
                                .name("error"));
                    } catch (Exception e) {
                        log.error("SSE 에러 메시지 전송 실패", e);
                    } finally {
                        emitter.completeWithError(error);
                    }
                },

                // onComplete: 스트리밍 완료 시
                () -> {
                    if (!isConnected.get()) {
                        log.info("SSE 스트리밍 완료 (클라이언트 이미 연결 종료됨)");
                        return;
                    }

                    sessionInfo.complete();

                    // 완료 메시지 생성
                    String finishReason = sessionInfo.getFinishReason();
                    ChatCompletionChunk.Usage usage = new ChatCompletionChunk.Usage(
                            sessionInfo.getPromptTokens(),
                            sessionInfo.getCompletionTokens(),
                            sessionInfo.getTotalTokens()
                    );
                    ChatCompletionChunk completionData = ChatCompletionChunk.completion(
                            UUID.randomUUID().toString(),
                            sessionInfo.getStartTime().toEpochSecond(java.time.ZoneOffset.UTC),
                            sessionInfo.getModel(),
                            finishReason != null ? finishReason : "stop",
                            usage
                    );

                    try {
                        emitter.send(SseEmitter.event()
                                .data(objectMapper.writeValueAsString(completionData))
                                .name("chunk"));
                        log.info("SSE 스트리밍 완료");
                        
                        // 스트리밍 완료 후 콜백 실행 (Spring AI의 saveAll이 완료된 후)
                        if (onCompleteCallback != null) {
                            try {
                                // 약간의 지연을 두어 Spring AI의 saveAll이 완료되도록 함
                                Thread.sleep(100);
                                onCompleteCallback.run();
                            } catch (Exception e) {
                                log.error("스트리밍 완료 콜백 실행 실패", e);
                            }
                        }
                    } catch (Exception e) {
                        log.error("SSE 완료 메시지 전송 실패", e);
                    } finally {
                        emitter.complete();
                    }
                }
        );

        // 클라이언트 연결 종료 콜백 등록 - Flux 구독 취소하여 AI 모델 호출도 중단
        emitter.onCompletion(() -> {
            if (isConnected.compareAndSet(true, false)) {
                log.info("SSE 연결 완료됨 - 구독 취소");
                disposable.dispose();
            }
        });

        emitter.onTimeout(() -> {
            if (isConnected.compareAndSet(true, false)) {
                log.warn("SSE 타임아웃 발생 - 구독 취소");
                disposable.dispose();
            }
        });

        emitter.onError(e -> {
            if (isConnected.compareAndSet(true, false)) {
                // 클라이언트 연결 종료로 인한 IOException은 예상된 상황이므로 WARN 레벨로 처리
                if (e instanceof java.io.IOException) {
                    log.warn("SSE 클라이언트 연결 종료 - 구독 취소: {}", e.getMessage());
                } else {
                    log.error("SSE 에러 발생 - 구독 취소", e);
                }
                disposable.dispose();
            }
        });
    }
}
