package com.kade.AIAssistant.feature.conversation.dto.response;

import java.time.Duration;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 스트리밍 세션의 실시간 정보를 누적하는 클래스
 */
@Data
public class StreamingSessionInfo {

    /**
     * 세션 시작 시간
     */
    private final LocalDateTime startTime = LocalDateTime.now();

    /**
     * 총 토큰 수 (누적)
     */
    private int totalTokens = 0;

    /**
     * 프롬프트 토큰 수
     */
    private int promptTokens = 0;

    /**
     * 완료 토큰 수 (누적)
     */
    private int completionTokens = 0;

    /**
     * 총 처리 시간 (초 단위, 누적)
     */
    private double totalDuration = 0.0;

    /**
     * 받은 청크 수
     */
    private int chunkCount = 0;

    /**
     * 응답 완료 이유 (마지막 청크에서 설정)
     */
    private String finishReason;

    /**
     * 세션 종료 시간
     */
    private LocalDateTime endTime;

    /**
     * 사용된 모델명
     */
    private String model;

    /**
     * 완료 여부
     */
    private boolean completed = false;

    /**
     * 누적된 ASSISTANT 응답 content (스트리밍 완료 후 저장용)
     */
    private final StringBuilder accumulatedContent = new StringBuilder();

    /**
     * 청크당 평균 토큰 수 계산
     */
    public double getAverageTokensPerChunk() {
        return chunkCount > 0 ? (double) completionTokens / chunkCount : 0;
    }

    /**
     * 초당 토큰 처리 속도 계산
     */
    public double getTokensPerSecond() {
        return totalDuration > 0 ? totalTokens / totalDuration : 0;
    }

    /**
     * 전체 세션 시간 계산 (초 단위)
     */
    public double getSessionDuration() {
        if (endTime == null) {
            return 0.0;
        }
        return Duration.between(startTime, endTime).toMillis() / 1000.0;
    }

    /**
     * 세션 완료 처리
     */
    public void complete() {
        this.completed = true;
        this.endTime = LocalDateTime.now();
    }

    /**
     * ASSISTANT 응답 content 누적
     */
    public void appendContent(String content) {
        if (content != null && !content.isEmpty()) {
            this.accumulatedContent.append(content);
        }
    }

    /**
     * 누적된 ASSISTANT 응답 content 반환
     */
    public String getAccumulatedContent() {
        return accumulatedContent.toString();
    }
}
