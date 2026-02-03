package com.kade.AIAssistant.feature.conversation.service;

import com.kade.AIAssistant.common.enums.PromptType;
import com.kade.AIAssistant.common.prompt.PromptService;
import com.kade.AIAssistant.feature.conversation.dto.request.AssistantRequest;
import com.kade.AIAssistant.infra.langfuse.prompt.LangfusePromptTemplate;
import com.kade.AIAssistant.infra.ollama.factory.OllamaChatModelFactory;
import io.opentelemetry.api.trace.Span;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

@Service
@Slf4j
@RequiredArgsConstructor
public class ModelExecuteService {

    // TODO: 프롬프트(기능) 별로 동적으로 변동하여 사용할 수 있도록 변경할 것
    @Value("${spring.ai.ollama.chat.model:default}")
    private String MODEL_NAME;

    @Value("${app.subject-generation.timeout-seconds:30}")
    private int subjectGenerationTimeoutSeconds;

    @Value("${app.streaming.retry.max-attempts:3}")
    private int retryMaxAttempts;

    @Value("${app.streaming.retry.initial-backoff-ms:100}")
    private int retryInitialBackoffMs;

    @Value("${app.streaming.retry.max-backoff-ms:2000}")
    private int retryMaxBackoffMs;

    private final PromptService promptService;
    private final OllamaChatModelFactory chatModelFactory;
    private final ChatMemory chatMemory;

    /**
     * AI 모델 스트리밍 생성 - ChatClient 고수준 API 사용
     */
    public Flux<ChatResponse> stream(String userId, AssistantRequest request) {
        Span.current().setAttribute("langfuse.trace.metadata.promptType", request.promptType().name());

        LangfusePromptTemplate template = promptService.getLangfusePrompt(request.promptType());
        Prompt prompt = buildPrompt(userId, request, template);
        OllamaChatOptions options = template.getOllamaChatOptions(MODEL_NAME);

        // 기존 팩토리로 ChatModel 생성 (기본 옵션 포함)
        OllamaChatModel chatModel = chatModelFactory.getChatModel(MODEL_NAME, request.promptType(), options);

        // ChatClient + MessageChatMemoryAdvisor (대화 기록 로드만 사용, 저장은 우리가 직접)
        // MessageChatMemoryAdvisor는 대화 시작 전 이전 메시지를 자동으로 로드하여 컨텍스트로 제공
        // 저장은 ConversationService에서 직접 처리하므로, Advisor의 저장 기능은 CustomChatMemoryRepository의 중복 체크로 방지됨
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();

        String conversationId = StringUtils.hasText(request.conversationId())
                ? request.conversationId()
                : ChatMemory.DEFAULT_CONVERSATION_ID;

        return chatClient
                .prompt(prompt)
                .options(options)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .chatResponse()
                .retryWhen(Retry.backoff(retryMaxAttempts, Duration.ofMillis(retryInitialBackoffMs))
                        .maxBackoff(Duration.ofMillis(retryMaxBackoffMs))
                        .doBeforeRetry(signal ->
                                log.warn("Ollama 스트리밍 호출 재시도 {}/{}: {}",
                                        signal.totalRetriesInARow() + 1,
                                        retryMaxAttempts,
                                        signal.failure().getMessage())));
    }

    /**
     * 고정 system + (선택) 사용자 선호 system + user 메시지 순으로 Prompt 생성
     */
    private Prompt buildPrompt(String userId, AssistantRequest request, LangfusePromptTemplate template) {
        List<Message> messages = new ArrayList<>();
        messages.add(promptService.getSystemPrompt(template, request));
        promptService.getUserPreferencePrompt(userId).ifPresent(messages::add);
        messages.add(UserMessage.builder().text(request.question()).build());
        return new Prompt(messages);
    }

    /**
     * 첫 요청 내용(질문)을 요약해 대화 제목으로 쓸 문자열을 생성한다. 동기 호출. Langfuse의 PromptType.SUBJECT 프롬프트 템플릿을 사용한다.
     *
     * @param question 사용자 질문(첫 메시지)
     * @return 요약 제목, 실패 시 질문 앞 200자
     */
    public String generateConversationSubject(String question) {
        if (!StringUtils.hasText(question)) {
            return "(제목 없음)";
        }
        try {
            // Langfuse 또는 Redis 에서 프롬프트 템플릿 가져옴
            LangfusePromptTemplate langfusePromptTemplate = promptService.getLangfusePrompt(PromptType.SUBJECT);

            AssistantRequest subRequest = new AssistantRequest(PromptType.SUBJECT, question, null, null, null);
            // 시스템 프롬프트 생성
            Message systemPrompt = promptService.getSystemPrompt(langfusePromptTemplate, subRequest);
            // 옵션
            OllamaChatOptions options = langfusePromptTemplate.getOllamaChatOptions(MODEL_NAME);
            // 유저 프롬프트
            Message userPrompt = UserMessage.builder().text(question).build();

            Prompt prompt = new Prompt(List.of(systemPrompt, userPrompt));
            OllamaChatModel chatModel = chatModelFactory.getChatModel(MODEL_NAME, PromptType.SUBJECT, options);

            // ChatClient로 동기 호출(stream과 동일한 경로) + 타임아웃으로 무한 대기 방지
            ChatClient chatClient = ChatClient.builder(chatModel).build();
            ChatResponse response = CompletableFuture
                    .supplyAsync(() -> chatClient.prompt(prompt).options(options).call().chatResponse())
                    .orTimeout(subjectGenerationTimeoutSeconds, TimeUnit.SECONDS)
                    .join();

            if (response != null && response.getResult() != null && response.getResult().getOutput() != null) {
                String content = response.getResult().getOutput().getText();
                if (StringUtils.hasText(content)) {
                    String trimmed = content.trim();
                    return trimmed.length() > 32 ? trimmed.substring(0, 32) : trimmed;
                }
            }
        } catch (Exception e) {
            log.warn("제목 생성 실패, 질문 앞부분 사용: {}", e.getMessage());
        }
        return question.length() > 32 ? question.substring(0, 32) : question;
    }
}
