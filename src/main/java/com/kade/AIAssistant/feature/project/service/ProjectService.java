package com.kade.AIAssistant.feature.project.service;

import com.kade.AIAssistant.feature.project.dto.reqeust.CreateProjectRequest;
import com.kade.AIAssistant.feature.project.entity.UserProjectEntity;
import com.kade.AIAssistant.feature.project.repository.UserProjectRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProjectService {

    private final UserProjectRepository projectRepository;

    /**
     * 새 프로젝트를 생성하여 DB에 저장한다. project_id는 코드에서 생성한 UUID를 사용한다.
     *
     * @return 생성된 프로젝트의 project_id (UUID)
     */
    public UUID createProject(String userId, CreateProjectRequest request) {
        UUID projectId = UUID.randomUUID();
        UserProjectEntity entity = new UserProjectEntity(userId, projectId.toString(), request.subject());
        projectRepository.save(entity);
        return projectId;
    }
}
