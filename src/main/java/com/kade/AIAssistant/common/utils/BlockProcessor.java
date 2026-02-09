package com.kade.AIAssistant.common.utils;

import com.kade.AIAssistant.common.enums.ModelBlockOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 모델별 블록 제거 및 텍스트 정제 유틸리티
 * <p>
 * 각 AI 모델은 추론 과정을 다른 태그로 감싸므로, ModelBlockConfig를 통해 동적으로 블록을 제거합니다.
 * </p>
 */
public final class BlockProcessor {

    private BlockProcessor() {
        // 유틸 클래스 인스턴스화 금지
    }

    /**
     * 지정된 모델의 블록을 제거하고 텍스트 정제
     *
     * @param rawContent 원본 텍스트
     * @param config     모델별 블록 설정
     * @return 정제된 텍스트
     */
    public static String stripBlocks(String rawContent, ModelBlockOption config) {
        if (rawContent == null || rawContent.isEmpty()) {
            return "";
        }

        Pattern pattern = config.getBlockPattern();
        String cleaned = pattern.matcher(rawContent).replaceAll(" ");

        // 미완성 태그도 제거 (예: "<think" 또는 "</think>")
        String tagPattern = String.format("(?i)</?%s[^>]*>", config.getBlockTagName());
        cleaned = cleaned.replaceAll(tagPattern, " ");

        // 연속된 공백과 탭만 정리 (개행은 유지)
        cleaned = cleaned.replaceAll("[ \\t]+", " ");

        return cleaned;
    }

    /**
     * 버퍼에서 완성된 블록 제거
     *
     * @param buffer 텍스트 버퍼
     * @param config 모델별 블록 설정
     */
    public static void removeCompleteBlocks(StringBuilder buffer, ModelBlockOption config) {
        Pattern pattern = config.getBlockPattern();
        while (true) {
            Matcher matcher = pattern.matcher(buffer);
            if (!matcher.find()) {
                return;
            }
            buffer.replace(matcher.start(), matcher.end(), " ");
        }
    }

    /**
     * 버퍼에서 전송 가능한 텍스트 추출
     * <p>
     * 블록 시작 태그 이전까지의 텍스트만 추출하여 반환합니다. 스트리밍 중 블록 내용이 클라이언트에게 전송되지 않도록 합니다.
     * </p>
     *
     * @param buffer 텍스트 버퍼
     * @param config 모델별 블록 설정
     * @return 전송 가능한 텍스트
     */
    public static String drainSendableText(StringBuilder buffer, ModelBlockOption config) {
        if (buffer.length() == 0) {
            return "";
        }

        String startTag = config.getStartTag();
        int thinkIdx = indexOfIgnoreCase(buffer, startTag);
        int endIdx = thinkIdx >= 0 ? thinkIdx : buffer.length();

        if (endIdx <= 0) {
            return "";
        }

        String sendable = buffer.substring(0, endIdx);
        buffer.delete(0, endIdx);
        return stripBlocks(sendable, config);
    }

    /**
     * 대소문자 구분 없이 문자열 찾기
     */
    private static int indexOfIgnoreCase(StringBuilder source, String target) {
        String lowerSource = source.toString().toLowerCase();
        return lowerSource.indexOf(target.toLowerCase());
    }
}
