package com.kade.AIAssistant.infra.redis.enums;

public enum RedisKeyPrefix {
    SYSTEM_PROMPT("system_prompt"),
    USER_PREFERENCE_PROMPT("user_preference_prompt"),
    CHAT_MEMORY("chat_memory"),
    IDEMPOTENCY("idempotency"),
    USER_STATISTIC("user_statistic");

    private final String value;

    RedisKeyPrefix(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}

