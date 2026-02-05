package com.kade.AIAssistant.common.enums;

import java.util.regex.Pattern;

/**
 * AI 모델별 블록 태그 설정
 * <p>
 * 각 AI 모델은 추론 과정을 다른 태그로 감싸므로,
 * 이를 동적으로 처리하기 위한 설정을 제공합니다.
 * </p>
 */
public enum ModelBlockConfig {
    /**
     * Claude 모델 - &lt;think&gt; 블록 사용
     */
    CLAUDE("think"),

    /**
     * Gemini 모델 - &lt;thinking&gt; 블록 사용
     */
    GEMINI("thinking"),

    /**
     * GPT 모델 - &lt;reasoning&gt; 블록 사용
     */
    GPT("reasoning");

    private final String blockTagName;
    private final Pattern blockPattern;
    private final String startTag;

    ModelBlockConfig(String blockTagName) {
        this.blockTagName = blockTagName;
        this.startTag = "<" + blockTagName;
        // 정규식 패턴을 미리 컴파일하여 성능 최적화
        String regex = String.format("<%s>.*?</%s>", blockTagName, blockTagName);
        this.blockPattern = Pattern.compile(regex, Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    }

    /**
     * 블록 태그 이름 반환
     */
    public String getBlockTagName() {
        return blockTagName;
    }

    /**
     * 블록 매칭을 위한 정규식 Pattern 반환
     * <p>
     * 성능을 위해 미리 컴파일된 패턴을 반환합니다.
     * </p>
     */
    public Pattern getBlockPattern() {
        return blockPattern;
    }

    /**
     * 블록 시작 태그 반환 (예: "&lt;think")
     * <p>
     * 스트리밍 중 블록 시작을 감지하는데 사용됩니다.
     * </p>
     */
    public String getStartTag() {
        return startTag;
    }
}
