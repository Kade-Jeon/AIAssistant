package com.kade.AIAssistant.feature.functional.service;

import com.kade.AIAssistant.common.prompt.PromptService;
import com.kade.AIAssistant.domain.reqeust.AssistantRequest;
import com.kade.AIAssistant.infra.langfuse.prompt.LangfusePromptTemplate;
import com.kade.AIAssistant.infra.ollama.factory.OllamaChatModelFactory;
import io.opentelemetry.api.trace.Span;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@Slf4j
@RequiredArgsConstructor
public class ModelExecuteService {

    // TODO: 프롬프트(기능) 별로 동적으로 변동하여 사용할 수 있도록 변경할 것
    @Value("${spring.ai.ollama.chat.model:default}")
    private String MODEL_NAME;

    private final PromptService promptService;
    private final OllamaChatModelFactory chatModelFactory;

    /**
     * AI 모델 스트리밍 생성 - ChatClient 고수준 API 사용
     */
    public Flux<ChatResponse> stream(AssistantRequest request) {
        // 검색/필터용 trace-level attribute (gateway가 모르는 값은 앱에서 세팅)
        Span.current().setAttribute("langfuse.trace.metadata.promptType", request.promptType().name());

        // Langfuse 또는 Redis 에서 프롬프트 템플릿 가져옴
        LangfusePromptTemplate langfusePromptTemplate = promptService.getLangfusePrompt(request.promptType());
        // 시스템 프롬프트 생성
        Message systemPrompt = promptService.getSystemPrompt(langfusePromptTemplate, request);
        // 옵션
        OllamaChatOptions options = langfusePromptTemplate.getOllamaChatOptions(MODEL_NAME);

        // 유저 프롬프트
        Message userPrompt = UserMessage.builder()
                .text(request.question())
                .build();

        Prompt prompt = new Prompt(List.of(systemPrompt, userPrompt));

        // 기존 팩토리로 ChatModel 생성 (기본 옵션 포함)
        OllamaChatModel chatModel = chatModelFactory.getChatModel(MODEL_NAME, request.promptType(), options);

        // ChatClient로 스트리밍 (Flux<ChatResponse>)
        ChatClient chatClient = ChatClient.create(chatModel);

        return chatClient
                .prompt(prompt)
                .options(options)
                .stream()
                .chatResponse();
    }
}
