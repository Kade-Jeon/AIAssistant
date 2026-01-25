package com.kade.AIAssistant.common.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 추론 작업시, <think> 블록 제거 및 텍스트 정제 유틸리티
 */
public final class ThinkBlockProcessor {

    private static final Pattern THINK_BLOCK_PATTERN =
            Pattern.compile("<think>.*?</think>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final String THINK_START_TAG = "<think";

    private ThinkBlockProcessor() {
        // 유틸 클래스 인스턴스화 금지
    }

    public static String stripThinkBlocks(String rawContent) {
        if (rawContent == null || rawContent.isEmpty()) {
            return "";
        }

        String cleaned = THINK_BLOCK_PATTERN.matcher(rawContent).replaceAll(" ");
        cleaned = cleaned.replaceAll("(?i)</?think>", " ");

        // 연속된 공백을 하나로 정리
        cleaned = cleaned.replaceAll("\\s+", " ");

        return cleaned;
    }

    /**
     * 버퍼에서 완성된 think 블록 제거
     */
    public static void removeCompleteThinkBlocks(StringBuilder buffer) {
        while (true) {
            Matcher matcher = THINK_BLOCK_PATTERN.matcher(buffer);
            if (!matcher.find()) {
                return;
            }
            buffer.replace(matcher.start(), matcher.end(), " ");
        }
    }

    /**
     * 버퍼에서 전송 가능한 텍스트 추출
     */
    public static String drainSendableText(StringBuilder buffer) {
        if (buffer.length() == 0) {
            return "";
        }

        int thinkIdx = indexOfIgnoreCase(buffer, THINK_START_TAG);
        int endIdx = thinkIdx >= 0 ? thinkIdx : buffer.length();

        if (endIdx <= 0) {
            return "";
        }

        String sendable = buffer.substring(0, endIdx);
        buffer.delete(0, endIdx);
        return stripThinkBlocks(sendable);
    }

    /**
     * 대소문자 구분 없이 문자열 찾기
     */
    private static int indexOfIgnoreCase(StringBuilder source, String target) {
        String lowerSource = source.toString().toLowerCase();
        return lowerSource.indexOf(target.toLowerCase());
    }
}


