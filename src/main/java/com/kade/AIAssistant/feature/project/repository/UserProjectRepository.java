package com.kade.AIAssistant.feature.project.repository;

import com.kade.AIAssistant.feature.project.entity.UserProjectEntity;
import com.kade.AIAssistant.feature.project.entity.UserProjectId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 유저–프로젝트 소유 매핑 조회/저장.
 */
public interface UserProjectRepository extends JpaRepository<UserProjectEntity, UserProjectId> {

    boolean existsById_UserIdAndId_ProjectId(String userId, String projectId);

    Optional<UserProjectEntity> findById_UserIdAndId_ProjectId(String userId, String projectId);

    List<UserProjectEntity> findById_UserIdOrderByUpdatedAtDesc(String userId, Pageable pageable);

    int deleteById_UserIdAndId_ProjectId(String userId, String projectId);
}
