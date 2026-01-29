# AI Assistant

## 프로젝트 개요

- **프로젝트 이름**: AI Assistant
- **한 줄 요약**: Spring AI 기반 대화형 AI 어시스턴트 서비스 (SSE 스트리밍, RAG, 관찰성 포함)
- **개발 목적**:
    - Spring AI 프레임워크를 활용한 실전 AI 서비스 구현 경험
    - 대용량 대화 히스토리 관리와 성능 최적화 학습
    - 프로덕션 수준의 관찰성(Observability) 구축
- **실제로 해결하려던 문제**:
    - 대화 컨텍스트 관리: 수천 건의 대화 히스토리를 효율적으로 저장/조회
    - 실시간 스트리밍: AI 응답을 청크 단위로 전송하여 사용자 경험 개선
    - 프롬프트 관리: Langfuse를 통한 프롬프트 버전 관리 및 A/B 테스트 기반 마련
    - 파일 기반 RAG: PDF, HWP 등 문서를 첨부하여 AI가 문서 내용을 참조하여 답변

## 기술 스택

### 언어 / 프레임워크

- Java 21 (Virtual Threads)
- Spring Boot 3.5.9
- Spring AI 1.1.2
- Reactor (Project Reactor)

### 인프라 / 미들웨어

- Ollama (로컬 LLM 서버)
- Langfuse (프롬프트 관리 및 관찰성)
- OpenTelemetry (분산 추적)

### DB / 캐시

- PostgreSQL (대화 히스토리 영구 저장)
- Redis (채팅 컨텍스트 캐싱, 프롬프트 캐싱)

### 기타

- Apache Tika (문서 텍스트 추출)
- HWP 라이브러리 (한글 문서 지원)
- SSE (Server-Sent Events) 스트리밍

## 실행 방법

프로젝트 실행을 위해 필요한 인프라(Redis, PostgreSQL, Langfuse 등)를 Docker Compose로 실행합니다.

```bash
cd docker
docker compose up -d
```

- **Langfuse**: 프롬프트 관리 및 Observability (포트 3000)
- **Redis (Langfuse용)**: Langfuse 내부 캐싱 (포트 6379)
- **PostgreSQL (Langfuse용)**: Langfuse 메타데이터 저장 (포트 5432)
- **ClickHouse**: Langfuse 이벤트 저장
- **MinIO**: Langfuse 파일 저장
- **redis-app**: 애플리케이션 캐싱용 Redis (포트 6389)
- **postgres-app**: 애플리케이션 데이터 저장용 PostgreSQL (포트 54321, 데이터베이스: aichat)

애플리케이션 실행:

```bash
./gradlew bootRun
```

애플리케이션은 `http://localhost:8080`에서 실행됩니다.

Langfuse는 `http://localhost:3000`에서 실행됩니다.

## 핵심 구현 포인트

### 1. Cache-Aside 패턴으로 이중 저장 전략

**설계 고민**:

- Spring AI의 기본 `JdbcChatMemoryRepository`는 `SPRING_AI_CHAT_MEMORY` 테이블을 사용하지만, 우리는 `CHAT_MESSAGE` 테이블에 `message_id`(
  UUID)를 포함한 자체 스키마가 필요했음
- 대화 조회 시 매번 RDB 조회는 성능 병목이 될 수 있음

**선택 이유**:

- `CustomChatMemoryRepository`로 `ChatMemoryRepository` 인터페이스 구현
- `RedisChatMemory`가 `ChatMemory` 인터페이스를 구현하여 Cache-Aside 패턴 적용
    - `get()`: Redis 조회 → miss 시 RDB 조회 → Redis 캐싱
    - `add()`: RDB 저장은 하지 않고 Redis 캐시만 갱신 (실제 저장은 `ConversationService`에서 직접 처리)
    - `clear()`: RDB 삭제 + Redis 삭제

**트레이드오프**:

- ✅ 장점: RDB 조회 비용 절감, 대화 시작 시 지연 시간 감소
- ⚠️ 단점: Redis 장애 시 RDB로 폴백하지만 일시적 성능 저하 가능, 캐시 일관성 관리 필요

**구현 세부사항**:

```java
// RedisChatMemory.java
@Override
public List<Message> get(String conversationId) {
    String cacheKey = cacheKey(conversationId);
    Optional<Object> cached = cache.get(cacheKey);

    if (cached.isPresent()) {
        return fromJson(json); // Redis 히트
    }

    // Redis 미스 → RDB 조회 → 캐싱
    List<Message> fromDb = repository.findByConversationId(conversationId);
    writeCacheWithTimestamp(conversationId, fromDb);
    return fromDb;
}
```

### 2. MessageChatMemoryAdvisor와 저장 로직 분리

**설계 고민**:

- Spring AI의 `MessageChatMemoryAdvisor`는 대화 시작 전 이전 메시지를 자동으로 로드하여 컨텍스트로 제공
- 하지만 저장은 `ConversationService`에서 직접 처리해야 함 (파일 첨부하며 요청하는 케이스에서 "사용자 요청:" 부분만 저장하기 위해)

**선택 이유**:

- `MessageChatMemoryAdvisor`는 컨텍스트 로드만 담당
- `RedisChatMemory.add()`는 저장하지 않고 캐시만 갱신
- 실제 저장은 `ConversationService.saveUserMessage()`, `saveAssistantMessage()`에서 처리
- `CustomChatMemoryRepository.saveAll()`에서 중복 체크로 Advisor의 저장 시도 방지

**트레이드오프**:

- ✅ 장점: 저장 로직 완전 제어, 파일 첨부 형식 처리 가능
- ⚠️ 단점: Spring AI 기본 동작과 다르므로 코드 이해 필요

### 3. SSE 스트리밍과 Reactor 통합

**설계 고민**:

- Spring AI의 `ChatClient.stream()`은 `Flux<ChatResponse>` 반환
- `SseEmitter.send()`는 블로킹 I/O일 수 있음
- 클라이언트 연결 종료 시 AI 모델 호출도 중단해야 함

**선택 이유**:

- Reactor의 `publishOn()`으로 전용 Scheduler에 오프로딩
- `Disposable`을 저장하여 클라이언트 연결 종료 시 `dispose()` 호출
- `AtomicBoolean`으로 연결 상태 추적하여 로그 폭탄 방지

**구현 세부사항**:

```java
// StreamingService.java
Flux<ChatResponse> offloadedStream = chatResponseStream.publishOn(sseStreamingScheduler);

Disposable disposable = offloadedStream.subscribe(
        chatResponse -> { /* SSE 전송 */ },
        error -> { /* 에러 처리 */ },
        () -> { /* 완료 처리 + 콜백 실행 */ }
);

emitter.

onCompletion(() ->disposable.

dispose());
        emitter.

onTimeout(() ->disposable.

dispose());
        emitter.

onError(e ->disposable.

dispose());
```

### 4. 파일 첨부 형식과 메시지 저장 분리

**설계 고민**:

- 파일 첨부 시: "다음 첨부파일(문서) 내용:\n\n{파일 내용}\n\n사용자 요청: {질문}"
- AI 호출에는 전체를 보내지만, 저장은 "사용자 요청:" 이후만 저장해야 함

**선택 이유**:

- `extractUserRequestFromFileAttachment()` 메서드로 분리
- `ConversationService.saveUserMessage()`에서 저장 전 변환
- `RedisChatMemory.toStoredMessage()`에서도 동일 로직 적용

**트레이드오프**:

- ✅ 장점: 저장 공간 절약, 대화 히스토리 가독성 향상
- ⚠️ 단점: 파일 내용은 저장되지 않으므로 나중에 참조 불가 (메타데이터만 저장)

### 5. 페이징 조회와 캐시 병합

**설계 고민**:

- 대화 목록 조회 시 스크롤 업으로 이전 메시지 조회 필요
- 페이징 조회 결과를 기존 캐시와 병합해야 함

**선택 이유**:

- `getConversation()`에서 `beforeTimestamp` 파라미터로 페이징 조회
- `RedisChatMemory.getWithPaging()`에서 기존 캐시와 병합
- `mergeMessages()`에서 content + messageType을 키로 중복 제거

**구현 세부사항**:

```java
// RedisChatMemory.java
public List<Message> getWithPaging(String conversationId, Instant beforeTimestamp, int limit) {
    List<Message> pagedMessages = ((CustomChatMemoryRepository) repository)
            .findByConversationIdAndTimestampBefore(conversationId, beforeTimestamp, limit);

    List<Message> cached = get(conversationId);
    List<Message> merged = mergeMessages(cached, pagedMessages);
    writeCache(conversationId, merged);
    return merged;
}
```

## 아키텍처 설명

### 전체 흐름

```
[클라이언트]
    ↓ HTTP POST /api/v1/ai/conv
[ConversationController]
    ↓ streamToSse()
[ConversationService]
    ├─ saveUserMessage() → CHAT_MESSAGE 테이블 저장
    ├─ ModelExecuteService.stream()
    │   ├─ PromptService.getLangfusePrompt() → Langfuse/Redis에서 프롬프트 조회
    │   ├─ ChatClient.builder(chatModel)
    │   │   └─ MessageChatMemoryAdvisor → RedisChatMemory.get() 호출
    │   │       ├─ Redis 조회 (히트 시 즉시 반환)
    │   │       └─ 미스 시 CustomChatMemoryRepository.findByConversationId()
    │   │           └─ CHAT_MESSAGE 테이블 조회 → Redis 캐싱
    │   └─ stream() → Flux<ChatResponse> 반환
    └─ StreamingService.streamToSse()
        ├─ Reactor publishOn() → 전용 Scheduler
        ├─ SSE 전송 (chunk 이벤트)
        └─ 완료 시 saveAssistantMessage() → CHAT_MESSAGE 테이블 저장
```

### 레이어 구조

```
com.kade.AIAssistant/
├── feature/              # 비즈니스 로직
│   └── conversation/
│       ├── controller/   # REST API 엔드포인트
│       ├── service/      # 핵심 비즈니스 로직
│       ├── entity/       # JPA 엔티티
│       └── repository/   # 데이터 접근 계층
├── infra/                # 외부 시스템 통합
│   ├── redis/            # Redis 캐시 구현
│   ├── ollama/           # Ollama 클라이언트 팩토리
│   └── langfuse/         # Langfuse 클라이언트 및 관찰성
├── common/               # 공통 유틸리티
│   ├── prompt/           # 프롬프트 서비스
│   ├── exceptions/       # 예외 처리
│   └── utils/            # 유틸리티 클래스
├── config/               # Spring 설정
└── domain/               # DTO (Request/Response)
```

### 왜 이런 구조를 선택했는지

1. **feature 중심 패키징**: 도메인별로 기능을 묶어 확장성 확보
2. **infra 분리**: 외부 시스템(Redis, Ollama, Langfuse) 의존성을 명확히 분리하여 테스트 용이성 향상
3. **SOLID 원칙 준수**:
    - Single Responsibility: 각 서비스가 명확한 책임 (ConversationService는 오케스트레이션, ModelExecuteService는 AI 호출)
    - Dependency Inversion: 인터페이스(`ChatMemory`, `ChatMemoryRepository`)에 의존

## 문제 해결 사례

### 1. MessageChatMemoryAdvisor의 중복 저장 문제

**문제**:

- `MessageChatMemoryAdvisor`가 대화 종료 시 `ChatMemory.add()`를 호출하여 저장 시도
- `ConversationService`에서도 이미 저장했으므로 중복 저장 발생

**원인 분석**:

- Spring AI의 기본 동작: Advisor가 자동으로 저장
- 우리 요구사항: 저장 로직을 직접 제어해야 함 (파일 첨부 형식 처리)

**해결 방법**:

- `CustomChatMemoryRepository.saveAll()`에서 중복 체크 로직 추가
- content + type을 키로 사용하여 기존 메시지와 비교
- 중복이면 스킵, 새 메시지만 저장

**결과**:

- 중복 저장 방지
- `RedisChatMemory.add()`는 저장하지 않고 캐시만 갱신하도록 변경하여 이중 저장 문제 해결

### 2. SSE 스트리밍 중 클라이언트 연결 종료 시 리소스 누수

**문제**:

- 클라이언트가 연결을 끊어도 AI 모델 호출이 계속 진행되어 리소스 낭비
- `SseEmitter`는 연결 종료를 감지하지만 `Flux` 구독은 계속됨

**원인 분석**:

- Reactor의 `Flux`는 구독 취소(`dispose()`)하지 않으면 계속 실행
- `SseEmitter`의 콜백(`onCompletion`, `onTimeout`, `onError`)과 `Flux` 구독이 분리되어 있음

**해결 방법**:

- `Disposable`을 변수로 저장
- `emitter.onCompletion()`, `onTimeout()`, `onError()` 콜백에서 `disposable.dispose()` 호출
- `AtomicBoolean`으로 연결 상태 추적하여 중복 dispose 방지

**결과**:

- 클라이언트 연결 종료 시 즉시 AI 모델 호출 중단
- 서버 리소스 절약

### 3. 페이징 조회 시 캐시 일관성 문제

**문제**:

- 대화 목록 조회 시 스크롤 업으로 이전 메시지를 조회하면 기존 캐시와 병합 필요
- 단순 append 시 중복 메시지 발생 가능

**원인 분석**:

- `RedisChatMemory.warmCache()`는 단순 병합만 수행
- 페이징 조회 결과와 기존 캐시에 동일 메시지가 있을 수 있음

**해결 방법**:

- `mergeMessages()` 메서드 구현
- content + messageType을 키로 사용하여 중복 제거
- timestamp 기준으로 정렬하여 최신 메시지가 앞에 오도록 유지

**결과**:

- 페이징 조회 시에도 캐시 일관성 유지
- 중복 메시지 없이 정렬된 메시지 리스트 반환

## 확장/개선 가능성

### 현재 한계

1. **Vector DB 미구현**: RAG는 파일 첨부 형식으로만 지원, 벡터 검색 기반 RAG는 미구현
2. **프롬프트 버전 관리**: Langfuse 연동은 되어 있으나 프롬프트 버전 롤백/롤포워드 기능 미구현
3. **멀티 모델 지원**: 현재는 개발 PC성능 이슈로 단일 모델(`qwen2.5:1.5b`)만 사용, 모델별 동적 라우팅 미지원
4. **대화 히스토리 압축**: 오래된 대화는 자동 압축/아카이빙 기능 없음
5. **에이전틱 기능 미지원**: 함수 호출(Function Calling), 도구 사용(Tool Use), 멀티 스텝 추론 등 에이전트 기능 없음. 단순 질의응답 형태의 대화만 지원

### 실무 기준으로 추가하고 싶은 것

1. **Rate Limiting**: 사용자별/API별 요청 제한 (Redis 기반)
2. **모니터링 대시보드**: Langfuse 트레이스를 활용한 대시보드 구축
3. **A/B 테스트**: 프롬프트 버전별 성능 비교 기능
4. **벡터 검색 RAG**: Milvus/Pinecone 연동하여 문서 임베딩 기반 검색
5. **스트리밍 재시도**: 네트워크 오류 시 자동 재시도 로직
6. **대화 내보내기**: PDF/JSON 형식으로 대화 내보내기 기능
7. **관리자 API**: 사용자별 대화 통계, 모델 성능 모니터링
