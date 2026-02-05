package com.kade.AIAssistant.infra.langfuse.dto.response;

/**
 * Langfuse observations 목록 응답의 meta (utilsMetaResponse) 스키마.
 */
public record ObservationsMetaDto(
        int page,
        int limit,
        int totalItems,
        int totalPages
) {
}
