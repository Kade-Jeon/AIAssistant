-- pgvector 확장 (RAG 벡터 검색용)
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 벡터 저장소 테이블 (Spring AI PgVectorStore)
-- metadata JSON에 conversation_id, user_id, filename 저장하여 프로젝트별 필터링
CREATE TABLE IF NOT EXISTS vector_store (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    content TEXT,
    metadata json,
    embedding vector(1024)
);

CREATE INDEX IF NOT EXISTS vector_store_embedding_idx
ON vector_store USING HNSW (embedding vector_cosine_ops);

-- 채팅 메시지 테이블 (안정적인 id로 모든 대화 히스토리 저장)
-- id는 안정적으로 유지되므로 CHAT_ATTACHMENT와 FK 제약 가능
CREATE TABLE IF NOT EXISTS CHAT_MESSAGE (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id VARCHAR(36) NOT NULL,
    content TEXT NOT NULL,
    type VARCHAR(20) NOT NULL,
    "timestamp" TIMESTAMP NOT NULL
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

-- 유저별 프로젝트 소유 매핑 (project_id = conversation_id 통일, 프로젝트 생성 시 USER_CONVERSATION에도 등록)
CREATE TABLE IF NOT EXISTS USER_PROJECT (
    user_id VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(36) NOT NULL,
    subject VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (user_id, conversation_id)
);

CREATE INDEX IF NOT EXISTS USER_PROJECT_USER_ID_IDX ON USER_PROJECT(user_id);

-- 프로젝트별 업로드 문서 목록 (벡터 저장 시 파일명 등록, 목록 조회용, chat_attachment와 동일하게 size/mime_type 보관)
CREATE TABLE IF NOT EXISTS PROJECT_DOCUMENT (
    id BIGSERIAL PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    filename VARCHAR(512) NOT NULL,
    mime_type VARCHAR(100),
    size BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS PROJECT_DOCUMENT_PROJECT_USER_IDX ON PROJECT_DOCUMENT(conversation_id, user_id);

-- 채팅 메시지 첨부파일 메타데이터 (message_id로 CHAT_MESSAGE 참조)
-- FK 제약 추가: CHAT_MESSAGE의 id는 안정적이므로 FK 제약 가능
-- CASCADE 삭제는 JPA에서 처리 (orphanRemoval = true)
CREATE TABLE IF NOT EXISTS CHAT_ATTACHMENT (
    id BIGSERIAL PRIMARY KEY,
    message_id UUID NOT NULL REFERENCES CHAT_MESSAGE(id),
    conversation_id VARCHAR(36) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100),
    size BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS CHAT_ATTACHMENT_MESSAGE_ID_IDX ON CHAT_ATTACHMENT(message_id);
CREATE INDEX IF NOT EXISTS CHAT_ATTACHMENT_CONVERSATION_ID_IDX ON CHAT_ATTACHMENT(conversation_id);

-- 사용자 테이블
CREATE TABLE IF NOT EXISTS "user" (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email_id VARCHAR(255) NOT NULL UNIQUE,
    plan VARCHAR(20) NOT NULL DEFAULT 'FREE',
    password VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS USER_EMAIL_ID_IDX ON "user"(email_id);

-- 사용자 선호도 테이블
-- CASCADE 삭제는 JPA에서 처리 (orphanRemoval = true)
CREATE TABLE IF NOT EXISTS user_preference (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES "user"(id),
    nickname VARCHAR(30),
    occupation VARCHAR(30),
    extra_info VARCHAR(300),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS USER_PREFERENCE_USER_ID_IDX ON user_preference(user_id);

-- 초기 사용자 데이터 삽입 (중복 방지)
-- ON CONFLICT로 중복 방지
INSERT INTO "user" (id, email_id, plan, password, created_at, updated_at)
VALUES (gen_random_uuid(), 'kade@kade.com', 'FREE', '1234', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (email_id) DO NOTHING;