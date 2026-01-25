package com.kade.AIAssistant.infra.redis.enums;

public enum RedisKeyPrefix {
    PROMPT("prompt");

    private final String value;

    RedisKeyPrefix(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}

