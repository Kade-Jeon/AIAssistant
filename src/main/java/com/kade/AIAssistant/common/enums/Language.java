package com.kade.AIAssistant.common.enums;

import java.util.Arrays;

public enum Language {

    KOREAN("ko", "한국어", "Korean"),
    ENGLISH("en", "영어", "English"),
    JAPANESE("ja", "일본어", "Japanese"),
    CHINESE("zh", "중국어", "Chinese"),
    CHINESE_SIMPLIFIED("zh_cn", "중국어(간체)", "Chinese (Simplified)"),
    CHINESE_TRADITIONAL("zh_tw", "중국어(번체)", "Chinese (Traditional)"),
    SPANISH("es", "스페인어", "Spanish"),
    FRENCH("fr", "프랑스어", "French"),
    GERMAN("de", "독일어", "German"),
    RUSSIAN("ru", "러시아어", "Russian"),
    PORTUGUESE("pt", "포르투갈어", "Portuguese"),
    ITALIAN("it", "이탈리아어", "Italian"),
    VIETNAMESE("vi", "베트남어", "Vietnamese"),
    THAI("th", "태국어", "Thai"),
    ARABIC("ar", "아랍어", "Arabic"),
    HINDI("hi", "힌디어", "Hindi"),
    INDONESIAN("id", "인도네시아어", "Indonesian"),
    TURKISH("tr", "터키어", "Turkish"),
    POLISH("pl", "폴란드어", "Polish"),
    DUTCH("nl", "네덜란드어", "Dutch"),
    SWEDISH("sv", "스웨덴어", "Swedish");

    private final String code;
    private final String koreanName;
    private final String englishName;

    Language(String code, String koreanName, String englishName) {
        this.code = code;
        this.koreanName = koreanName;
        this.englishName = englishName;
    }

    public String getCode() {
        return code;
    }

    public String getKoreanName() {
        return koreanName;
    }

    public String getEnglishName() {
        return englishName;
    }

    /**
     * 언어 코드로 Language enum을 찾습니다.
     *
     * @param code ISO 639-1 언어 코드 (예: "en", "ko")
     * @return 해당하는 Language enum
     * @throws IllegalArgumentException 지원하지 않는 언어 코드인 경우
     */
    public static Language fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("언어 코드는 null이거나 비어있을 수 없습니다.");
        }

        return Arrays.stream(values())
                .filter(language -> language.code.equalsIgnoreCase(code.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "지원하지 않는 언어 코드입니다: " + code));
    }

    /**
     * 언어 코드를 한글 언어명으로 변환합니다. 지원하지 않는 언어 코드인 경우 원본 코드를 반환합니다.
     *
     * @param code ISO 639-1 언어 코드 (예: "en", "ko")
     * @return 한글 언어명 또는 원본 코드
     */
    public static String toKoreanName(String code) {
        if (code == null || code.trim().isEmpty()) {
            return code;
        }

        try {
            return fromCode(code).getKoreanName();
        } catch (IllegalArgumentException e) {
            // 지원하지 않는 언어 코드인 경우 원본 반환
            return code;
        }
    }

    /**
     * 언어 코드를 영문 언어명으로 변환합니다. 지원하지 않는 언어 코드인 경우 원본 코드를 반환합니다.
     *
     * @param code ISO 639-1 언어 코드 (예: "en", "ko")
     * @return 영문 언어명 또는 원본 코드
     */
    public static String toEnglishName(String code) {
        if (code == null || code.trim().isEmpty()) {
            return code;
        }

        try {
            return fromCode(code).getEnglishName();
        } catch (IllegalArgumentException e) {
            // 지원하지 않는 언어 코드인 경우 원본 반환
            return code;
        }
    }

    /**
     * 해당 언어 코드가 지원되는지 확인합니다.
     *
     * @param code ISO 639-1 언어 코드
     * @return 지원 여부
     */
    public static boolean isSupported(String code) {
        if (code == null || code.trim().isEmpty()) {
            return false;
        }

        return Arrays.stream(values())
                .anyMatch(language -> language.code.equalsIgnoreCase(code.trim()));
    }
}
