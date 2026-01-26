-- Spring AI JDBC Chat Memory (PostgreSQL)
-- JdbcChatMemoryRepository auto-config 제외 시 수동 초기화용
CREATE TABLE IF NOT EXISTS SPRING_AI_CHAT_MEMORY (
    conversation_id VARCHAR(36) NOT NULL,
    content TEXT NOT NULL,
    type VARCHAR(10) NOT NULL CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    "timestamp" TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS SPRING_AI_CHAT_MEMORY_CONVERSATION_ID_TIMESTAMP_IDX
ON SPRING_AI_CHAT_MEMORY(conversation_id, "timestamp");

-- 유저별 대화 소유 매핑 (어떤 유저가 어떤 conversationId를 쓰는지)
-- created_at, updated_at 은 JPA Auditing으로 채움
CREATE TABLE IF NOT EXISTS USER_CONVERSATION (
    user_id VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(36) NOT NULL,
    subject VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (user_id, conversation_id)
);

CREATE INDEX IF NOT EXISTS USER_CONVERSATION_USER_ID_IDX ON USER_CONVERSATION(user_id);
