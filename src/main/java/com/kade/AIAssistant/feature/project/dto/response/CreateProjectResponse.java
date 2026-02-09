package com.kade.AIAssistant.feature.project.dto.response;

import com.kade.AIAssistant.feature.project.entity.UserProjectEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record CreateProjectResponse(
        @NotBlank(message = "프로젝트 이름을 입력해주세요.")
        @Size(min = 1, max = 32)
        String subject,
        @NotNull(message = "프로젝트 ID는 필수입니다.")
        UUID conversationId,
        @NotNull(message = "생성 일시는 필수입니다.")
        Instant createdAt,
        @NotNull(message = "수정 일시는 필수입니다.")
        Instant updatedAt
) {
    public static CreateProjectResponse from(UserProjectEntity entity) {
        return new CreateProjectResponse(
                entity.getSubject(),
                UUID.fromString(entity.getId().getConversationId()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
