package com.kade.AIAssistant.agent.service;

import com.kade.AIAssistant.feature.project.service.ProjectRagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class RagService {

    private final ProjectRagService projectRagService;
    private final ThreadLocal<RagContext> contextHolder = new ThreadLocal<>();

    /**
     * RAG 컨텍스트 설정 (userId, projectId).
     * ModelExecuteService에서 스트리밍 시작 전 호출.
     */
    public void setContext(String userId, String projectId) {
        contextHolder.set(new RagContext(userId, projectId));
    }

    /**
     * RAG 컨텍스트 정리.
     * 스트리밍 완료 후 호출.
     */
    public void clearContext() {
        contextHolder.remove();
    }

    /**
     * 현재 컨텍스트의 userId, projectId로 벡터 검색.
     * RagTools에서 호출.
     */
    public String searchDocuments(String query) {
        RagContext context = contextHolder.get();
        if (context == null || context.projectId == null) {
            log.warn("RAG 컨텍스트가 설정되지 않음");
            return "";
        }

        log.info("벡터 검색 시작: projectId={}, query={}", context.projectId, query);
        return projectRagService.searchAsContext(context.userId, context.projectId, query);
    }

    private record RagContext(String userId, String projectId) {
    }
}
