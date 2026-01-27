package com.kade.AIAssistant.domain.response;

import java.time.Instant;

/**
 * 첨부파일 메타데이터 응답 DTO.
 */
public record AttachmentDto(
        String filename,
        String mimeType,
        Long size,
        Instant createdAt
) {
}
