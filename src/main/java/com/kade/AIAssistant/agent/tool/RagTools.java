package com.kade.AIAssistant.agent.tool;

import com.kade.AIAssistant.agent.service.RagService;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

/**
 * AI Tool: 프로젝트 문서 벡터 검색.
 * AI가 사용자 질문에 답하기 위해 프로젝트 문서를 검색할 때 사용.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RagTools {

    private final RagService ragService;

    /**
     * 프로젝트 문서 검색 도구.
     * AI가 사용자 질문과 관련된 프로젝트 문서를 찾을 때 사용.
     */
    @Bean
    @Description("프로젝트에 업로드된 문서에서 질문과 관련된 내용을 검색합니다. 사용자가 프로젝트 문서에 대해 질문하거나 문서 내용을 참조해야 할 때 사용하세요.")
    public Function<SearchDocumentsRequest, SearchDocumentsResponse> searchProjectDocuments() {
        return request -> {
            log.info("AI가 문서 검색 요청: query={}", request.query());
            String result = ragService.searchDocuments(request.query());
            return new SearchDocumentsResponse(result);
        };
    }

    public record SearchDocumentsRequest(
            @Description("검색할 질문이나 키워드")
            String query
    ) {
    }

    public record SearchDocumentsResponse(
            @Description("검색된 문서 내용")
            String content
    ) {
    }
}
