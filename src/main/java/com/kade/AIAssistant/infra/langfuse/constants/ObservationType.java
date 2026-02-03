package com.kade.AIAssistant.infra.langfuse.constants;

/**
 * GENERATION : LLM 호출 그 자체
 * <p>
 * SPAN : 작업구간 (부모/자식 단계)
 * <p>
 * EVENT : 단일 이벤트 로그
 * <p>
 * AGENT : 에이전트 실행 단위
 * <p>
 * TOOL : 함수/툴 호출
 * <p>
 * CHAIN : 여러 단계 묶음
 * <p>
 * RETRIEVER : RAG 검색 단계
 * <p>
 * EVALUATOR : 평가 결과
 * <p>
 * EMBEDDING : 임베딩 생성
 * <p>
 * GUARDRAIL : 정책/필터 검사
 */
public enum ObservationType {
    GENERATION,
    SPAN,
    EVENT,
    AGENT,
    TOOL,
    CHAIN,
    RETRIEVER,
    EVALUATOR,
    EMBEDDING,
    GUARDRAIL
}
