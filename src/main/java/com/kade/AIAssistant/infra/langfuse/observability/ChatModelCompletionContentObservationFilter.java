package com.kade.AIAssistant.infra.langfuse.observability;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation.Context;
import io.micrometer.observation.ObservationFilter;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class ChatModelCompletionContentObservationFilter implements ObservationFilter {

    @Override
    public Context map(Context context) {
        if (!(context instanceof ChatModelObservationContext chatModelObservationContext)) {
            // ChatModelObservationContext 아니면 불필요하므로 필터 동작안하고 바로 반환
            // 즉, AI 모델의 완성된 결과(Completion Content) 처리 외에는 반환
            log.debug("Context is NOT ChatModelObservationContext. Skipping filter. Type: {}",
                    context.getClass().getName());
            return context;
        }

        // Process Prompts
        List<Message> instructions = chatModelObservationContext.getRequest().getInstructions();
        if (!CollectionUtils.isEmpty(instructions)) {
            for (int i = 0; i < instructions.size(); i++) {
                Message message = instructions.get(i);
                chatModelObservationContext.addHighCardinalityKeyValue(
                        KeyValue.of("gen_ai.prompt." + i + ".role", message.getMessageType().getValue()));
                chatModelObservationContext.addHighCardinalityKeyValue(
                        KeyValue.of("gen_ai.prompt." + i + ".content", message.getText()));
            }
        }

        // Process Completions
        ChatResponse response = chatModelObservationContext.getResponse();
        if (response != null && response.getResults() != null) {
            List<Generation> results = response.getResults();
            for (int i = 0; i < results.size(); i++) {
                Generation generation = results.get(i);
                if (generation.getOutput() != null && StringUtils.hasText(generation.getOutput().getText())) {
                    chatModelObservationContext.addHighCardinalityKeyValue(
                            KeyValue.of("gen_ai.completion." + i + ".role", "assistant"));
                    chatModelObservationContext.addHighCardinalityKeyValue(
                            KeyValue.of("gen_ai.completion." + i + ".content", generation.getOutput().getText()));
                }
            }
        }

        return chatModelObservationContext;
    }
}
