package com.kade.AIAssistant.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * pgvector 기반 벡터 저장소 설정.
 * RAG 문서 청킹 후 임베딩을 저장하고 유사도 검색에 사용.
 */
@Configuration
@Slf4j
public class VectorStoreConfig {

    private static final int EMBEDDING_DIMENSIONS = 768; // nomic-embed-text

    @Bean
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        log.info("PgVectorStore 빈 초기화 (dimensions={})", EMBEDDING_DIMENSIONS);
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(EMBEDDING_DIMENSIONS)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(false) // schema.sql에서 테이블 관리
                .schemaName("public")
                .vectorTableName("vector_store")
                .build();
    }
}
