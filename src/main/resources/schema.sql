-- 채팅 메시지 테이블 (안정적인 message_id로 모든 대화 히스토리 저장)
-- message_id는 안정적으로 유지되므로 CHAT_ATTACHMENT와 FK 제약 가능
CREATE TABLE IF NOT EXISTS CHAT_MESSAGE (
    message_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id VARCHAR(36) NOT NULL,
    content TEXT NOT NULL,
    type VARCHAR(10) NOT NULL CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    "timestamp" TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS CHAT_MESSAGE_CONVERSATION_ID_TIMESTAMP_IDX
ON CHAT_MESSAGE(conversation_id, "timestamp" DESC);

-- Spring AI JDBC Chat Memory (PostgreSQL) - 더 이상 사용하지 않음 (제거됨)
-- 이전에는 JdbcChatMemoryRepository가 SPRING_AI_CHAT_MEMORY 테이블을 사용했으나,
-- 현재는 CustomChatMemoryRepository가 CHAT_MESSAGE 테이블을 사용합니다.
-- SPRING_AI_CHAT_MEMORY 테이블은 더 이상 필요하지 않으므로 제거되었습니다.

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

-- 채팅 메시지 첨부파일 메타데이터 (message_id로 CHAT_MESSAGE 참조)
-- FK 제약 추가: CHAT_MESSAGE의 message_id는 안정적이므로 FK 제약 가능
CREATE TABLE IF NOT EXISTS CHAT_ATTACHMENT (
    id BIGSERIAL PRIMARY KEY,
    message_id UUID NOT NULL REFERENCES CHAT_MESSAGE(message_id) ON DELETE CASCADE,
    conversation_id VARCHAR(36) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100),
    size BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS CHAT_ATTACHMENT_MESSAGE_ID_IDX ON CHAT_ATTACHMENT(message_id);
CREATE INDEX IF NOT EXISTS CHAT_ATTACHMENT_CONVERSATION_ID_IDX ON CHAT_ATTACHMENT(conversation_id);
