package com.kade.AIAssistant.feature.project.service;

import com.kade.AIAssistant.common.exceptions.customs.ForbiddenException;
import com.kade.AIAssistant.feature.conversation.service.DocumentService;
import com.kade.AIAssistant.feature.project.repository.UserProjectRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * RAG 기술 구현 (역할 구분: docs/RAG_SERVICE_ROLES.md 참고).
 * 항상 userId, projectId를 인자로 받아 벡터 저장/검색만 수행. "지금 대화" 같은 컨텍스트는 모름.
 * - addDocument: 텍스트 추출 → 청킹 → 임베딩 → VectorStore 저장
 * - search / searchAsContext: conversation_id, user_id 메타데이터로 유사도 검색
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProjectRagService {

    private static final int CHUNK_SIZE = 500;
    private static final int MIN_CHUNK_SIZE_CHARS = 200;
    private static final int DEFAULT_TOP_K = 5;

    private final DocumentService documentService;
    private final VectorStore vectorStore;
    private final UserProjectRepository userProjectRepository;
    private final TokenTextSplitter textSplitter;

    /**
     * 프로젝트에 문서를 추가한다. 텍스트 추출 → 청킹 → 임베딩 → 벡터 저장.
     *
     * @param userId    사용자 ID (USER-ID 헤더)
     * @param projectId 프로젝트 ID
     * @param file      업로드 파일 (PDF, HWP 등)
     * @throws ForbiddenException 프로젝트 소유자가 아닌 경우
     */
    public void addDocument(String userId, String projectId, MultipartFile file) {
        validateProjectOwnership(userId, projectId);

        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
        String text = documentService.extractText(file);

        if (!StringUtils.hasText(text)) {
            log.warn("문서에서 추출된 텍스트가 없음: projectId={}, filename={}", projectId, filename);
            return;
        }

        Map<String, Object> metadata = Map.of(
                "conversation_id", projectId,
                "user_id", userId,
                "filename", filename
        );
        Document doc = new Document(text, metadata);
        List<Document> chunks = textSplitter.apply(List.of(doc));

        for (Document chunk : chunks) {
            chunk.getMetadata().put("conversation_id", projectId);
            chunk.getMetadata().put("user_id", userId);
            chunk.getMetadata().put("filename", filename);
        }

        vectorStore.add(chunks);
        log.info("RAG 문서 추가 완료: projectId={}, filename={}, chunks={}", projectId, filename, chunks.size());
    }

    /**
     * 프로젝트 내 문서에서 유사도 검색.
     */
    public List<Document> search(String userId, String projectId, String query) {
        validateProjectOwnership(userId, projectId);

        String filterExpr = String.format("conversation_id == '%s' && user_id == '%s'",
                escapeFilterValue(projectId),
                escapeFilterValue(userId));

        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(DEFAULT_TOP_K)
                        .filterExpression(filterExpr)
                        .build()
        );
    }

    /**
     * 검색 결과를 AI 프롬프트 컨텍스트로 사용할 문자열로 변환.
     */
    public String searchAsContext(String userId, String projectId, String query) {
        List<Document> docs = search(userId, projectId, query);
        if (docs.isEmpty()) {
            return "";
        }
        return docs.stream()
                .map(Document::getText)
                .reduce((a, b) -> a + "\n\n---\n\n" + b)
                .orElse("");
    }

    private void validateProjectOwnership(String userId, String projectId) {
        if (!userProjectRepository.existsById_UserIdAndId_ConversationId(userId, projectId)) {
            throw new ForbiddenException("해당 프로젝트에 대한 접근 권한이 없습니다.");
        }
    }

    private String escapeFilterValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("'", "''");
    }

    /**
     * 프로젝트 삭제 시 벡터 저장소에서 해당 conversation_id, user_id 메타데이터를 가진 문서 삭제.
     */
    public void deleteByProject(String userId, String projectId) {
        validateProjectOwnership(userId, projectId);
        String filterExpr = String.format("conversation_id == '%s' && user_id == '%s'",
                escapeFilterValue(projectId),
                escapeFilterValue(userId));
        vectorStore.delete(filterExpr);
        log.info("프로젝트 벡터 삭제 완료: projectId={}, userId={}", projectId, userId);
    }

    /**
     * 단일 문서의 벡터 청크를 삭제한다. conversation_id, user_id, filename 메타데이터로 필터링.
     */
    public void deleteByDocument(String userId, String conversationId, String filename) {
        validateProjectOwnership(userId, conversationId);
        String filterExpr = String.format(
                "conversation_id == '%s' && user_id == '%s' && filename == '%s'",
                escapeFilterValue(conversationId),
                escapeFilterValue(userId),
                escapeFilterValue(filename));
        vectorStore.delete(filterExpr);
        log.info("문서 벡터 삭제 완료: conversationId={}, filename={}", conversationId, filename);
    }
}
