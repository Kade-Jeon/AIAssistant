package com.kade.AIAssistant.feature.project.dto.response;

import com.kade.AIAssistant.feature.project.entity.ProjectDocumentEntity;
import java.time.Instant;

public record ProjectDocumentResponse(
        Long documentId,
        String filename,
        String mimeType,
        Long size,
        Instant createdAt
) {

    public static ProjectDocumentResponse from(ProjectDocumentEntity entity) {
        return new ProjectDocumentResponse(
                entity.getId(),
                entity.getFilename(),
                entity.getMimeType(),
                entity.getSize(),
                entity.getCreatedAt()
        );
    }
}
