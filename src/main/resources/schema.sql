-- Spring AI JDBC Chat Memory (PostgreSQL)
-- JdbcChatMemoryRepository auto-config 제외 시 수동 초기화용
-- message_id: Spring AI는 4컬럼만 INSERT. PK는 DB DEFAULT gen_random_uuid()로 자동 생성.
CREATE TABLE IF NOT EXISTS SPRING_AI_CHAT_MEMORY (
    message_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
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
    subject VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (user_id, conversation_id)
);

CREATE INDEX IF NOT EXISTS USER_CONVERSATION_USER_ID_IDX ON USER_CONVERSATION(user_id);

-- 채팅 메시지 첨부파일 메타데이터 (message_id로 SPRING_AI_CHAT_MEMORY 참조)
-- 참고: FK 제약 없음 (Spring AI의 JdbcChatMemoryRepository가 saveAll 시 DELETE+INSERT 방식으로
-- 메시지를 교체하므로, FK가 있으면 DELETE 단계에서 FK 위반 발생. message_id는 매칭용으로만 사용)
CREATE TABLE IF NOT EXISTS CHAT_ATTACHMENT (
    id BIGSERIAL PRIMARY KEY,
    message_id UUID NOT NULL,
    conversation_id VARCHAR(36) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100),
    size BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS CHAT_ATTACHMENT_MESSAGE_ID_IDX ON CHAT_ATTACHMENT(message_id);
CREATE INDEX IF NOT EXISTS CHAT_ATTACHMENT_CONVERSATION_ID_IDX ON CHAT_ATTACHMENT(conversation_id);
