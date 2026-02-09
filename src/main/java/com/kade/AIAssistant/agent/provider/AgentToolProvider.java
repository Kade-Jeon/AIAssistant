package com.kade.AIAssistant.agent.provider;

import com.kade.AIAssistant.agent.tool.RagTools;
import com.kade.AIAssistant.feature.project.service.ProjectRagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentToolProvider {

    private final ProjectRagService projectRagService;

    /**
     * 요청별 컨텍스트(userId, projectId)로 RAG 검색 도구 생성. ThreadLocal 없이 생성자로 컨텍스트를 전달하여 boundedElastic 등 다른 스레드에서도 동작한다.
     */
    public Object[] getTools(String userId, String projectId) {
        return new Object[]{new RagTools(projectRagService, userId, projectId)};
    }
}
