package com.kade.AIAssistant.prompt;

import com.kade.AIAssistant.common.enums.PromptType;
import com.kade.AIAssistant.domain.reqeust.AssistantRequest;
import com.kade.AIAssistant.infra.langfuse.prompt.LangfusePromptTemplate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PromptService {

    private final PromptTemplateProvider promptTemplateProvider;

    public LangfusePromptTemplate getLangfusePrompt(PromptType promptType) {
        return promptTemplateProvider.getSystemPromptTemplate(promptType);
    }

    public Message getSystemPrompt(LangfusePromptTemplate langfusePromptTemplate, AssistantRequest request) {
        SystemPromptTemplate systemPromptTemplate =
                switch (request.promptType()) {
                    case TRANSLATE -> new SystemPromptTemplate.Builder()
//                            .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>')
//                                    .build())
                            .template(langfusePromptTemplate.prompt())
                            .build();
                    default ->
                        // 기본: {} 구분자 사용
                            new SystemPromptTemplate.Builder()
                                    .template(langfusePromptTemplate.prompt())
                                    .build();
                };

        Map<String, Object> map = request.promptType().formatVariable(request);
        return systemPromptTemplate.createMessage(map);
    }
}
