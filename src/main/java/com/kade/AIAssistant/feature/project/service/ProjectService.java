package com.kade.AIAssistant.feature.project.service;

import com.kade.AIAssistant.common.exceptions.customs.ForbiddenException;
import com.kade.AIAssistant.feature.project.dto.reqeust.CreateProjectRequest;
import com.kade.AIAssistant.feature.project.dto.response.CreateProjectResponse;
import com.kade.AIAssistant.feature.project.dto.response.ProjectDocumentResponse;
import com.kade.AIAssistant.feature.project.entity.ProjectDocumentEntity;
import com.kade.AIAssistant.feature.project.entity.UserProjectEntity;
import com.kade.AIAssistant.feature.project.repository.ProjectDocumentRepository;
import com.kade.AIAssistant.feature.project.repository.UserProjectRepository;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 프로젝트 도메인 진입점 (역할 구분: docs/RAG_SERVICE_ROLES.md 참고).
 * <ul>
 *   <li>파일 벡터 저장: 사용자 요청 → ProjectRagService + 문서 목록 등록</li>
 *   <li>문서 목록 반환: projectId/userId 기준 저장 문서 목록</li>
 * </ul>
 * RAG 컨텍스트 설정·정리는 스트리밍 계층(ModelExecuteService)에서 RagService를 직접 호출.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProjectService {

    private final UserProjectRepository projectRepository;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final ProjectRagService projectRagService;

    public List<CreateProjectResponse> getProjectList(String userId) {
        return projectRepository.findById_UserIdOrderByUpdatedAtDesc(userId, Pageable.unpaged())
                .stream()
                .map(CreateProjectResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 새 프로젝트를 생성하여 DB에 저장한다. project_id = conversation_id로 동일 UUID 사용. USER_PROJECT와 USER_CONVERSATION에 모두 등록하여
     * CHAT_MESSAGE/CHAT_ATTACHMENT와 통합.
     *
     * @return 생성된 프로젝트 정보 (projectId는 conversationId로도 사용됨)
     */
    public CreateProjectResponse createProject(String userId, CreateProjectRequest request) {
        UUID conversationId = UUID.randomUUID();
        String conversationIdStr = conversationId.toString();
        UserProjectEntity entity = new UserProjectEntity(userId, conversationIdStr, request.subject());
        projectRepository.save(entity);

        return CreateProjectResponse.from(entity);
    }

    /**
     * 사용자가 요청한 파일을 벡터로 저장하고, 문서 목록에 등록한다.
     */
    public void addDocument(String userId, String conversationId, MultipartFile file) {
        projectRagService.addDocument(userId, conversationId, file);
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
        String mimeType = file.getContentType();
        Long size = file.getSize() >= 0 ? file.getSize() : null;
        projectDocumentRepository.save(new ProjectDocumentEntity(conversationId, userId, filename, mimeType, size));
    }

    /**
     * projectId/userId에 해당하는 사용자가 저장한 문서 목록(파일명)을 최신순으로 반환한다.
     */
    public List<ProjectDocumentResponse> getDocumentList(String conversationId, String userId) {
        return projectDocumentRepository.findByConversationIdAndUserIdOrderByCreatedAtDesc(conversationId, userId)
                .stream()
                .map(ProjectDocumentResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 프로젝트에서 문서 한 건을 삭제한다. PROJECT_DOCUMENT 행 삭제 및 vector_store에서 해당 문서 청크 삭제.
     *
     * @param userId        사용자 ID (USER-ID 헤더)
     * @param conversationId 프로젝트(대화) ID
     * @param documentId    PROJECT_DOCUMENT.id (문자열)
     * @throws ForbiddenException 문서가 없거나, 해당 프로젝트 소유가 아닌 경우
     */
    public void deleteDocument(String userId, String conversationId, String documentId) {
        Long id;
        try {
            id = Long.parseLong(documentId);
        } catch (NumberFormatException e) {
            throw new ForbiddenException("유효하지 않은 문서 ID입니다.");
        }
        ProjectDocumentEntity entity = projectDocumentRepository.findById(id)
                .orElseThrow(() -> new ForbiddenException("문서를 찾을 수 없거나 해당 프로젝트에 속하지 않습니다."));
        if (!entity.getConversationId().equals(conversationId) || !entity.getUserId().equals(userId)) {
            throw new ForbiddenException("문서를 찾을 수 없거나 해당 프로젝트에 속하지 않습니다.");
        }
        projectRagService.deleteByDocument(userId, conversationId, entity.getFilename());
        projectDocumentRepository.delete(entity);
    }
}
