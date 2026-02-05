package com.kade.AIAssistant.common.utils;

import com.kade.AIAssistant.common.enums.ModelBlockConfig;

/**
 * 추론 작업시, &lt;think&gt; 블록 제거 및 텍스트 정제 유틸리티
 *
 * @deprecated {@link BlockProcessor}를 사용하세요.
 *             이 클래스는 하위 호환성을 위해 유지되며, 내부적으로 BlockProcessor를 호출합니다.
 */
@Deprecated
public final class ThinkBlockProcessor {

    private ThinkBlockProcessor() {
        // 유틸 클래스 인스턴스화 금지
    }

    /**
     * &lt;think&gt; 블록 제거 및 텍스트 정제
     *
     * @param rawContent 원본 텍스트
     * @return 정제된 텍스트
     * @deprecated {@link BlockProcessor#stripBlocks(String, ModelBlockConfig)}를 사용하세요.
     */
    @Deprecated
    public static String stripThinkBlocks(String rawContent) {
        return BlockProcessor.stripBlocks(rawContent, ModelBlockConfig.CLAUDE);
    }

    /**
     * 버퍼에서 완성된 think 블록 제거
     *
     * @param buffer 텍스트 버퍼
     * @deprecated {@link BlockProcessor#removeCompleteBlocks(StringBuilder, ModelBlockConfig)}를 사용하세요.
     */
    @Deprecated
    public static void removeCompleteThinkBlocks(StringBuilder buffer) {
        BlockProcessor.removeCompleteBlocks(buffer, ModelBlockConfig.CLAUDE);
    }

    /**
     * 버퍼에서 전송 가능한 텍스트 추출
     *
     * @param buffer 텍스트 버퍼
     * @return 전송 가능한 텍스트
     * @deprecated {@link BlockProcessor#drainSendableText(StringBuilder, ModelBlockConfig)}를 사용하세요.
     */
    @Deprecated
    public static String drainSendableText(StringBuilder buffer) {
        return BlockProcessor.drainSendableText(buffer, ModelBlockConfig.CLAUDE);
    }
}


