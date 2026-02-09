package com.kade.AIAssistant.feature.project.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 프로젝트에 업로드된 문서 메타데이터 (목록 조회용). 파일 벡터 저장 시 한 건 등록된다.
 */
@Entity
@Table(name = "PROJECT_DOCUMENT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectDocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false, length = 36)
    private String conversationId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "filename", nullable = false, length = 512)
    private String filename;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "size")
    private Long size;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ProjectDocumentEntity(String conversationId, String userId, String filename, String mimeType, Long size) {
        this.conversationId = conversationId;
        this.userId = userId;
        this.filename = filename != null ? filename : "unknown";
        this.mimeType = mimeType;
        this.size = size;
        this.createdAt = Instant.now();
    }
}
