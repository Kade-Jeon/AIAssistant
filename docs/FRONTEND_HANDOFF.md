# 프론트엔드 전달 사항 (SSE 스트리밍 · Idempotency · 에러)

백엔드 SSE 스트리밍 API와 재시도/Idempotency 연동 시 프론트엔드에서 참고할 스펙과 요구사항입니다.

---

## 1. SSE 이벤트 스펙

### 1.1 정상 흐름

| 순서 | event 이름 | data | 설명 |
|------|------------|------|------|
| 1 | `open` | `"connected"` | 연결 수립 |
| 2 | `conversation_created` | `{ "conversationId": "...", "subject": "..." }` | 신규 대화 생성 시 1회 (기존 대화 이어하기면 없을 수 있음) |
| 3 | `chunk` | JSON (아래 참고) | 스트리밍 청크 (여러 번) |
| 4 | `chunk` | JSON (completion, usage 포함) | 스트리밍 완료 청크 |
| 5 | `stream_complete` | (없음 또는 `{}`) | 스트리밍 정상 종료 |

**`stream_complete`를 받기 전에 연결이 끊기면** → 실패로 간주하고, 필요 시 재시도 대상으로 처리하면 됩니다.

### 1.2 에러 흐름

| event 이름 | data | 설명 |
|------------|------|------|
| `error` | **JSON 문자열** (구조화된 에러) | 스트리밍 중 오류 발생 시 1회 전송 후 연결 종료 |

#### 에러 data 구조 (JSON)

`event: error`일 때 `data`는 아래 형태의 **JSON 문자열**입니다. 파싱 후 사용하세요.

```json
{
  "code": "STREAMING_FAILED",
  "message": "AI 응답 생성 중 오류가 발생했습니다.",
  "retryable": true
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `code` | string | 에러 코드 (예: `STREAMING_FAILED`) |
| `message` | string | 사용자에게 보여줄 메시지 |
| `retryable` | boolean (선택) | `true`이면 재시도 권장(일시적 오류 가능성), `false`/없으면 재시도 비권장 |

- **재시도 판단**: `event === 'error'` 이거나 **`stream_complete`를 받기 전에 연결이 끊긴 경우** → 실패로 보고, `retryable === true`이면 프론트 재시도 로직 수행 권장.

---

## 2. Idempotency-Key (재시도 시 중복 방지)

### 2.1 목적

- 같은 요청을 **재시도할 때** 사용자 메시지가 중복 저장되지 않도록 함.
- 클라이언트가 **동일한 키**로 재요청하면, 백엔드가 이미 처리한 요청인지 구분합니다.

### 2.2 사용 방법

- **엔드포인트**: `POST /api/v1/ai/conv` (JSON body, 스트리밍 채팅)
- **헤더**: `X-Idempotency-Key: <클라이언트 생성 UUID>` (선택)
- **키 생성**: 메시지 전송 시마다 **한 번만** 생성 (예: `crypto.randomUUID()`). 재시도 시에는 **같은 키**를 그대로 사용.

### 2.3 백엔드 동작 요약

| 상황 | 백엔드 동작 | 프론트 권장 |
|------|-------------|-------------|
| 최초 요청 (키 없음) | 일반 스트리밍 | - |
| 최초 요청 (키 있음) | 사용자 메시지 저장 후 스트리밍 | - |
| 재요청 (같은 키, 이미 **완료**) | 스트리밍 없이 `event: already_completed` + `data: { "conversationId": "..." }` 한 번 보내고 연결 종료 | 기존 대화/메시지로 표시 |
| 재요청 (같은 키, 아직 **처리 중**) | **HTTP 409 Conflict** + JSON body | 409 수신 시 재시도 중단 또는 잠시 후 재조회 |

### 2.4 HTTP 409 Conflict (Idempotency 충돌)

동일한 `X-Idempotency-Key`로 요청이 **이미 처리 중**일 때 반환됩니다.

- **HTTP 상태 코드**: `409 Conflict`
- **Body**: JSON (기존 `ErrorResponse` 형식)

```json
{
  "errorCode": "REQUEST_IN_PROGRESS",
  "message": "동일한 Idempotency-Key로 요청이 이미 처리 중입니다.",
  "path": "/api/v1/ai/conv",
  "status": 409
}
```

- **프론트 동작 제안**: 409 수신 시 같은 키로 재전송하지 말고, 기존 요청이 끝날 때까지 대기하거나, 기존 스트림/대화를 그대로 사용.

### 2.5 이벤트: `already_completed`

같은 Idempotency-Key로 **이미 완료된 요청**을 다시 보낸 경우, 스트리밍 없이 아래 이벤트 한 번 보내고 연결을 닫습니다.

- **event**: `already_completed`
- **data**: JSON 문자열 `{ "conversationId": "<기존 대화 ID>" }`

프론트에서는 이 대화 ID로 기존 메시지 목록을 조회해 표시하면 됩니다.

---

## 3. 재시도 정책 (프론트엔드)

### 3.1 백엔드 재시도

- 백엔드는 **Ollama(AI) 스트리밍 호출**에 대해 이미 재시도합니다 (최대 3회, 지수 백오프).
- 따라서 **프론트에 도달하는 에러/끊김**은 “백엔드 재시도까지 모두 실패한 뒤”일 수 있습니다.

### 3.2 프론트 재시도 권장

- **재시도 대상**  
  - `event === 'error'` 인 경우  
  - **`stream_complete`를 받기 전에** 연결이 끊긴 경우 (네트워크 끊김, 타임아웃 등)
- **재시도 시**  
  - **동일한 `X-Idempotency-Key`**를 넣어서 같은 요청을 다시 보내면, 백엔드가 사용자 메시지 중복 저장을 방지합니다.
- **재시도 횟수/간격**  
  - 예: 최대 2~3회, 1초/2초 등 지수 백오프. (팀 정책에 맞게 조정)
- **재시도하지 않는 경우**  
  - `retryable === false` 이거나, 409 등 “이미 처리 중” 응답이면 같은 키로 재전송하지 않음.

---

## 4. 요약 체크리스트

| 항목 | 내용 |
|------|------|
| **에러 구조** | `event: error` 시 `data`는 JSON 문자열. `code`, `message`, `retryable` 필드 사용. |
| **실패 판단** | `stream_complete` 전 끊김 또는 `event: error` → 실패로 간주. |
| **Idempotency** | 재시도 시 `X-Idempotency-Key`에 **동일한 키** 사용 → 중복 메시지 방지. |
| **409** | 같은 키로 이미 처리 중이면 409 → 재전송 중단, 대기 또는 기존 스트림 활용. |
| **already_completed** | 같은 키로 이미 완료된 요청 → `already_completed` + `conversationId` → 해당 대화로 표시. |

이 스펙을 기준으로 프론트엔드 재시도 로직과 Idempotency-Key 연동을 구현하면 됩니다.
