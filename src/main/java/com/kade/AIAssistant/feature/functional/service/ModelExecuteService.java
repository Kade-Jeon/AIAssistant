package com.kade.AIAssistant.feature.functional.service;

import com.kade.AIAssistant.infra.ollama.factory.OllamaChatModelFactory;
import com.kade.AIAssistant.prompt.PromptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ModelExecuteService {

    private final PromptService promptService;
    private final OllamaChatModelFactory chatModelFactory;
}
