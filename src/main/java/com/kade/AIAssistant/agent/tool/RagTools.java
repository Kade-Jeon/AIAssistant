package com.kade.AIAssistant.agent.tool;

import com.kade.AIAssistant.feature.project.service.ProjectRagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 요청별 컨텍스트(userId, projectId)를 가진 RAG 검색 도구. ThreadLocal 없이 생성자로 컨텍스트를 받아 boundedElastic 등 다른 스레드에서도 동작한다.
 */
@Slf4j
public class RagTools {

    private final ProjectRagService projectRagService;
    private final String userId;
    private final String projectId;

    public RagTools(ProjectRagService projectRagService, String userId, String projectId) {
        this.projectRagService = projectRagService;
        this.userId = userId;
        this.projectId = projectId;
    }

    @Tool(description = """
            Search project-uploaded documents for content relevant to the user's question.
            USE THIS TOOL FIRST when answering questions about topics that may exist in the project documents.
            Call with the user's full question or key keywords. Returns retrieved document excerpts.
            """)
    public String searchProjectDocuments(
            @ToolParam(description = "User's question or search keywords") String query) {
        log.info("AI가 문서 검색 요청: userId={}, projectId={}, query={}", userId, projectId, query);
        return projectRagService.searchAsContext(userId, projectId, query);
    }
}
