package com.kade.AIAssistant.feature.project.repository;

import com.kade.AIAssistant.feature.project.entity.ProjectDocumentEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 프로젝트별 업로드 문서 목록 조회.
 */
public interface ProjectDocumentRepository extends JpaRepository<ProjectDocumentEntity, Long> {

    List<ProjectDocumentEntity> findByConversationIdAndUserIdOrderByCreatedAtDesc(String conversationId, String userId);

    void deleteByConversationIdAndUserId(String conversationId, String userId);
}
