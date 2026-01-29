package com.kade.AIAssistant.common.enums;

/**
 * 채팅 메시지 타입 (Spring AI MessageType과 일치)
 */
public enum MessageType {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL;

    /**
     * 문자열 값을 MessageType으로 변환 (대소문자 무시)
     */
    public static MessageType fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("MessageType 값이 null이거나 비어있습니다.");
        }
        try {
            return valueOf(value.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("지원하지 않는 MessageType입니다: " + value, e);
        }
    }

    /**
     * Spring AI MessageType과 호환되는 소문자 값 반환
     */
    public String getValue() {
        return name().toLowerCase();
    }
}
