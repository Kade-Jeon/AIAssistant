# 🤖 AI Assistant

> **Spring AI 기반 대화형 AI 어시스턴트 서비스**  
> SSE 스트리밍, Cache-Aside 패턴, RAG, 관찰성(Observability)을 구현한 실전 AI 서비스

<!-- 기술 스택 배지 -->
![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.9-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![Spring AI](https://img.shields.io/badge/Spring%20AI-1.1.2-6DB33F?style=flat-square&logo=spring&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7-DC382D?style=flat-square&logo=redis&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=flat-square&logo=postgresql&logoColor=white)
![Ollama](https://img.shields.io/badge/Ollama-Local%20LLM-000000?style=flat-square)

---

## 📋 목차

- [프로젝트 소개](#-프로젝트-소개)
- [시연 / 데모](#-시연--데모)
- [시스템 아키텍처](#-시스템-아키텍처)
- [기술 스택](#-기술-스택)
- [핵심 기술적 도전 과제](#-핵심-기술적-도전-과제)
- [프로젝트 구조](#-프로젝트-구조)
- [실행 방법](#-실행-방법)
- [학습 및 성장 포인트](#-학습-및-성장-포인트)
- [향후 발전 방향](#-향후-발전-방향)

---

## 🎯 프로젝트 소개

### 내가 해결하려는 문제

LLM 기반 AI 서비스를 구축할 때 다음과 같은 실제 문제들이 발생합니다:

| 문제             | 상세 설명                                |
|----------------|--------------------------------------|
| **대화 컨텍스트 관리** | 수천 건의 대화 히스토리를 효율적으로 저장/조회해야 함       |
| **실시간 응답 경험**  | AI 응답 완료까지 기다리면 UX 저하, 청크 단위 스트리밍 필요 |
| **프롬프트 버전 관리** | 프롬프트 변경 이력 추적 및 A/B 테스트 기반 마련        |
| **파일 기반 RAG**  | PDF, HWP 등 문서를 첨부하여 AI가 참조하도록 구현     |
| **관찰성 확보**     | AI 호출 추적, 프롬프트/응답 기록, 사용자별 모니터링      |

### 접근 방식

```
[문제 인식] → [기술 선택] → [설계 결정] → [구현 및 검증]
```

1. **Spring AI 프레임워크 활용**: ChatClient, ChatMemory, Advisor 패턴으로 표준화된 구조
2. **Cache-Aside 패턴**: Redis + PostgreSQL 이중 저장으로 성능과 영속성 확보
3. **Reactor 기반 SSE**: 비동기 스트리밍으로 실시간 응답 경험 제공
4. **OpenTelemetry + Langfuse**: 분산 추적 기반 관찰성 구축

### 주요 기능

| 기능                | 설명                              |
|-------------------|---------------------------------|
| 💬 **실시간 대화**     | SSE 기반 스트리밍 응답, 청크 단위 실시간 전송    |
| 📁 **파일 기반 RAG**  | PDF, HWP, HWPX 문서 첨부 및 내용 참조 답변 |
| 🧠 **대화 컨텍스트 관리** | 이전 대화 자동 로드, 페이징 조회 지원          |
| 📊 **관찰성**        | AI 호출 추적, 프롬프트 버전 관리, 사용자별 모니터링 |
| 🌐 **다국어 지원**     | 번역 프롬프트 기반 다국어 응답               |

---

## 🖥️ 시연 / 데모

<!-- 
TODO: 아래 섹션에 실제 서비스 화면 캡쳐를 추가해주세요.
권장 이미지:
1. 메인 대화 화면 (SSE 스트리밍 응답 중인 모습)
2. 파일 첨부 RAG 기능 시연
3. Langfuse 관찰성 대시보드
-->

### 서비스 화면

<details>
<summary>📸 메인 대화 화면</summary>

<!-- 이미지 추가 예시:
![메인 화면](./docs/images/main-screen.png)
-->

> 이미지 추가 예정

</details>

<details>
<summary>📸 SSE 스트리밍 응답</summary>

<!-- 
GIF로 스트리밍 응답이 청크 단위로 표시되는 모습을 캡쳐하면 좋습니다.
![스트리밍](./docs/images/streaming.gif)
-->

> 이미지 추가 예정

</details>

<details>
<summary>📸 파일 첨부 RAG 기능</summary>

<!-- 
PDF/HWP 파일을 첨부하고 해당 내용을 참조하여 답변하는 모습
![RAG](./docs/images/rag-demo.png)
-->

> 이미지 추가 예정

</details>

<details>
<summary>📸 Langfuse 관찰성 대시보드</summary>

<!-- 
Langfuse에서 AI 호출 트레이스가 기록되는 모습
![Langfuse](./docs/images/langfuse-dashboard.png)
-->

> 이미지 추가 예정

</details>

---

## 🏗️ 시스템 아키텍처

<!-- 
TODO: Mermaid 또는 이미지로 아키텍처 다이어그램 추가
권장 다이어그램:
1. 전체 시스템 구성도
2. 데이터 플로우 다이어그램
3. 컴포넌트 간 관계도
-->

### 전체 시스템 구성도

<details>
<summary>📐 아키텍처 다이어그램</summary>

<!-- 이미지 추가 예시:
![Architecture](./docs/images/architecture.png)
-->

> 아키텍처 다이어그램 추가 예정

</details>

### 데이터 플로우

```
[클라이언트]
    ↓ HTTP POST /api/v1/ai/conv (SSE)
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

### 레이어 아키텍처

```
┌─────────────────────────────────────────────────────────────┐
│                    Controller Layer                          │
│  ConversationController, RagController, PreferenceController │
├─────────────────────────────────────────────────────────────┤
│                     Service Layer                            │
│  ConversationService, ModelExecuteService, StreamingService  │
├─────────────────────────────────────────────────────────────┤
│                   Repository Layer                           │
│  CustomChatMemoryRepository, ChatMessageRepository           │
├─────────────────────────────────────────────────────────────┤
│                  Infrastructure Layer                        │
│  RedisChatMemory, LangfuseClient, OllamaChatModelFactory     │
└─────────────────────────────────────────────────────────────┘
```

---

## 🛠️ 기술 스택

### Core

| 기술              | 버전    | 선택 이유                        |
|-----------------|-------|------------------------------|
| **Java**        | 21    | Virtual Threads로 I/O 블로킹 최적화 |
| **Spring Boot** | 3.5.9 | 최신 Spring AI 지원, 생산성         |
| **Spring AI**   | 1.1.2 | LLM 통합 표준화, ChatClient API   |
| **Reactor**     | -     | 비동기 스트리밍, Backpressure 지원    |

### AI / LLM

| 기술              | 용도                   |
|-----------------|----------------------|
| **Ollama**      | 로컬 LLM 서버 (qwen2.5)  |
| **Langfuse**    | 프롬프트 관리, AI 관찰성      |
| **Apache Tika** | 문서 텍스트 추출 (PDF, HWP) |

### 인프라

| 기술                 | 용도                  |
|--------------------|---------------------|
| **PostgreSQL**     | 대화 히스토리 영구 저장       |
| **Redis**          | 채팅 컨텍스트 캐싱, 프롬프트 캐싱 |
| **OpenTelemetry**  | 분산 추적               |
| **Docker Compose** | 개발 환경 구성            |

---

## 🔥 핵심 기술적 도전 과제

### 1. Cache-Aside 패턴으로 이중 저장 전략

#### 문제 상황

- Spring AI의 기본 `JdbcChatMemoryRepository`는 자체 스키마(`SPRING_AI_CHAT_MEMORY`) 사용
- 우리는 `message_id`(UUID)를 포함한 자체 `CHAT_MESSAGE` 스키마가 필요
- 매 요청마다 RDB 조회 시 성능 병목 발생

#### 해결 방법

`ChatMemoryRepository`와 `ChatMemory` 인터페이스를 커스텀 구현하여 Cache-Aside 패턴 적용:

```java
// RedisChatMemory.java - Cache-Aside 패턴 구현
@Override
public List<Message> get(String conversationId) {
    String cacheKey = cacheKey(conversationId);
    Optional<Object> cached = cache.get(cacheKey);

    if (cached.isPresent()) {
        return fromJson(json); // Redis 히트 → 즉시 반환
    }

    // Redis 미스 → RDB 조회 → Redis 캐싱
    List<Message> fromDb = repository.findByConversationId(conversationId);
    writeCacheWithTimestamp(conversationId, fromDb);
    return fromDb;
}
```

#### 트레이드오프

| 장점                    | 단점                            |
|-----------------------|-------------------------------|
| RDB 조회 비용 절감          | Redis 장애 시 RDB 폴백으로 일시적 성능 저하 |
| 대화 시작 시 지연 시간 감소      | 캐시 일관성 관리 필요                  |
| Spring AI 인터페이스 호환 유지 | 구현 복잡도 증가                     |

---

### 2. SSE 스트리밍과 리소스 누수 방지

#### 문제 상황

- Spring AI의 `ChatClient.stream()`은 `Flux<ChatResponse>` 반환
- 클라이언트가 연결을 끊어도 AI 모델 호출이 계속 진행되어 리소스 낭비
- `SseEmitter`의 콜백과 `Flux` 구독이 분리되어 있음

#### 해결 방법

`Disposable`을 저장하여 연결 종료 시 즉시 구독 취소:

```java
// StreamingService.java - 리소스 누수 방지
Flux<ChatResponse> offloadedStream = chatResponseStream
                .publishOn(sseStreamingScheduler);

Disposable disposable = offloadedStream.subscribe(
        chatResponse -> { /* SSE 전송 */ },
        error -> { /* 에러 처리 */ },
        () -> { /* 완료 처리 + 콜백 실행 */ }
);

// 연결 종료 시 AI 호출 중단
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

#### 결과

- 클라이언트 연결 종료 시 즉시 AI 모델 호출 중단
- 서버 리소스 절약
- `AtomicBoolean`으로 중복 dispose 방지

---

### 3. MessageChatMemoryAdvisor 중복 저장 문제

#### 문제 상황

- `MessageChatMemoryAdvisor`가 대화 종료 시 자동으로 `ChatMemory.add()` 호출
- `ConversationService`에서도 이미 저장했으므로 중복 저장 발생
- 파일 첨부 시 전체 내용이 아닌 "사용자 요청:" 이후만 저장해야 함

#### 해결 방법

저장 로직을 서비스 레이어에서 직접 제어:

```java
// CustomChatMemoryRepository.java - 중복 저장 방지
@Override
public void saveAll(String conversationId, List<Message> messages) {
    List<Message> existingMessages = findByConversationId(conversationId);
    Set<String> existingKeys = existingMessages.stream()
            .map(m -> m.getContent() + "|" + m.getType())
            .collect(Collectors.toSet());

    // 중복이면 스킵, 새 메시지만 저장
    messages.stream()
            .filter(m -> !existingKeys.contains(m.getContent() + "|" + m.getType()))
            .forEach(this::save);
}
```

```java
// RedisChatMemory.java - 캐시만 갱신, 저장 안 함
@Override
public void add(String conversationId, List<Message> messages) {
    // 실제 저장은 ConversationService에서 처리
    // 여기서는 캐시만 갱신
    updateCacheOnly(conversationId, messages);
}
```

---

### 4. Think Block 자동 제거

#### 문제 상황

- 일부 LLM(DeepSeek, Qwen 등)은 추론 과정을 `<think>...</think>` 태그로 응답
- 최종 응답에서 이 태그를 제거해야 함
- 스트리밍 중에는 태그가 여러 청크에 걸쳐 있을 수 있음

#### 해결 방법

```java
// ThinkBlockProcessor.java - 정규식 기반 Think Block 제거
public String process(String content) {
    if (content == null)
        return null;

    // <think>...</think> 블록 제거
    String processed = THINK_PATTERN.matcher(content).replaceAll("");

    // 불완전한 태그 처리 (스트리밍 중)
    if (processed.contains("<think>") && !processed.contains("</think>")) {
        int startIdx = processed.indexOf("<think>");
        processed = processed.substring(0, startIdx);
    }

    return processed.trim();
}
```

---

### 5. 페이징 조회와 캐시 병합

#### 문제 상황

- 대화 목록 스크롤 업 시 이전 메시지 조회 필요
- 페이징 결과와 기존 캐시를 병합해야 함
- 단순 append 시 중복 메시지 발생

#### 해결 방법

```java
// RedisChatMemory.java - 페이징 조회 + 캐시 병합
public List<Message> getWithPaging(String conversationId,
                                   Instant beforeTimestamp,
                                   int limit) {
    List<Message> pagedMessages = repository
            .findByConversationIdAndTimestampBefore(
                    conversationId, beforeTimestamp, limit);

    List<Message> cached = get(conversationId);
    List<Message> merged = mergeMessages(cached, pagedMessages);
    writeCache(conversationId, merged);
    return merged;
}

private List<Message> mergeMessages(List<Message> cached,
                                    List<Message> newMessages) {
    // content + messageType을 키로 중복 제거
    Map<String, Message> messageMap = new LinkedHashMap<>();
    Stream.concat(cached.stream(), newMessages.stream())
            .forEach(m -> messageMap.putIfAbsent(
                    m.getContent() + "|" + m.getType(), m));
    return new ArrayList<>(messageMap.values());
}
```

---

## 📁 프로젝트 구조

```
src/main/java/com/kade/AIAssistant/
├── feature/                          # 비즈니스 로직 (기능별 패키징)
│   ├── conversation/
│   │   ├── controller/               # REST API 엔드포인트
│   │   │   ├── ConversationController.java   # 대화 API (SSE 스트리밍)
│   │   │   └── RagController.java            # 파일 업로드 RAG
│   │   ├── service/
│   │   │   ├── ConversationService.java      # 대화 오케스트레이션
│   │   │   ├── ModelExecuteService.java      # AI 모델 호출
│   │   │   ├── StreamingService.java         # SSE 스트리밍 처리
│   │   │   └── RagService.java               # 문서 추출 (Tika)
│   │   ├── entity/                   # JPA 엔티티
│   │   └── repository/               # 데이터 접근 계층
│   ├── preference/                   # 사용자 설정 기능
│   └── login/                        # 로그인 기능
│
├── infra/                            # 외부 시스템 통합
│   ├── redis/
│   │   ├── context/
│   │   │   ├── RedisChatMemory.java          # ChatMemory 구현 (Cache-Aside)
│   │   │   └── CustomChatMemoryRepository.java
│   │   └── prompt/
│   │       └── PromptCacheService.java       # 프롬프트 캐싱
│   ├── ollama/
│   │   └── factory/
│   │       └── OllamaChatModelFactory.java   # 모델 인스턴스 팩토리
│   └── langfuse/
│       ├── observability/                    # OpenTelemetry 통합
│       │   ├── LangfuseBaggageSpanProcessor.java
│       │   ├── LangfuseUserTrackingFilter.java
│       │   └── ChatModelCompletionContentObservationFilter.java
│       └── prompt/
│           └── LangfuseClient.java           # Langfuse API 클라이언트
│
├── common/                           # 공통 유틸리티
│   ├── prompt/
│   │   └── PromptService.java                # 프롬프트 조회 (캐시 + Langfuse)
│   ├── exceptions/                   # 예외 처리
│   └── utils/
│       ├── StreamingChunkProcessor.java      # 스트리밍 청크 처리
│       └── ThinkBlockProcessor.java          # Think 블록 제거
│
├── config/                           # Spring 설정
│   ├── RedisChatMemoryConfig.java
│   ├── OllamaConfig.java
│   └── ObservabilityConfig.java
│
└── domain/                           # DTO (Request/Response)
    ├── request/
    └── response/
```

### 설계 원칙

| 원칙                            | 적용                                                                       |
|-------------------------------|--------------------------------------------------------------------------|
| **Single Responsibility**     | 각 서비스가 명확한 책임 (ConversationService는 오케스트레이션, ModelExecuteService는 AI 호출) |
| **Dependency Inversion**      | 인터페이스(`ChatMemory`, `ChatMemoryRepository`)에 의존                          |
| **Feature-based Packaging**   | 도메인별로 기능을 묶어 확장성 확보                                                      |
| **Infrastructure Separation** | 외부 시스템(Redis, Ollama, Langfuse) 의존성을 명확히 분리                              |

---

## 🚀 실행 방법

### 1. 인프라 실행 (Docker Compose)

```bash
cd docker
docker compose up -d
```

실행되는 서비스:

| 서비스                   | 포트    | 용도              |
|-----------------------|-------|-----------------|
| Langfuse              | 3000  | 프롬프트 관리, 관찰성    |
| redis-app             | 6389  | 애플리케이션 캐싱       |
| postgres-app          | 54321 | 대화 히스토리 저장      |
| Redis (Langfuse)      | 6379  | Langfuse 내부 캐싱  |
| PostgreSQL (Langfuse) | 5432  | Langfuse 메타데이터  |
| ClickHouse            | -     | Langfuse 이벤트 저장 |
| MinIO                 | -     | Langfuse 파일 저장  |

### 2. Ollama 설치 및 모델 다운로드

```bash
# Ollama 설치 (macOS)
brew install ollama

# 모델 다운로드
ollama pull qwen2.5:1.5b

# Ollama 서버 실행
ollama serve
```

### 3. 애플리케이션 실행

```bash
./gradlew bootRun
```

- 애플리케이션: http://localhost:8080
- Langfuse: http://localhost:3000

### 4. API 테스트

```bash
# 대화 API (SSE 스트리밍)
curl -X POST http://localhost:8080/api/v1/ai/conv \
  -H "Content-Type: application/json" \
  -H "USER-ID: test-user" \
  -d '{"conversationId": "conv-1", "message": "안녕하세요?"}'
```

---

## 📚 학습 및 성장 포인트

### 이 프로젝트를 통해 배운 것

#### 1. Spring AI 프레임워크 이해

- `ChatClient`, `ChatMemory`, `ChatMemoryRepository` 인터페이스 구조
- `Advisor` 패턴을 통한 대화 컨텍스트 관리
- 기본 구현체의 한계와 커스텀 구현 필요성

#### 2. 캐시 전략 설계

- Cache-Aside 패턴의 실제 구현
- 캐시 일관성 관리의 복잡성
- TTL vs 명시적 무효화 트레이드오프

#### 3. 반응형 프로그래밍

- Reactor `Flux`/`Mono` 기반 비동기 처리
- SSE 스트리밍과 리소스 관리
- 클라이언트 연결 종료 시 적절한 정리

#### 4. 관찰성(Observability) 구축

- OpenTelemetry + Langfuse 통합
- 분산 추적의 필요성과 구현 방법
- 커스텀 Span Processor 구현

#### 5. 기술적 의사결정 경험

- Spring AI 기본 구현체 vs 커스텀 구현 (저장 로직 제어 필요)
- Redis vs RDB 단일 저장소 (성능 + 영속성 둘 다 필요)
- 동기 vs 비동기 처리 (UX를 위한 스트리밍 선택)

---

## 🗺️ 향후 발전 방향

### 로드맵

```
Phase 1: 현재 (MVP)
├── ✅ SSE 스트리밍 대화
├── ✅ Cache-Aside 패턴
├── ✅ 파일 첨부 RAG
└── ✅ Langfuse 관찰성

Phase 2: 단기 목표
├── 🔲 Vector DB 기반 RAG (Milvus/Pinecone)
├── 🔲 Rate Limiting (Redis 기반)
├── 🔲 스트리밍 재시도 로직
├── 🔲 ElasticSearch 내용 검색 도입
└── 🔲 대화 내보내기 (PDF/JSON)

Phase 3: 중기 목표
├── 🔲 멀티 모델 지원 (동적 라우팅)
├── 🔲 프롬프트 A/B 테스트
├── 🔲 대화 히스토리 압축/아카이빙
└── 🔲 관리자 대시보드

Phase 4: 장기 목표
├── 🔲 에이전틱 기능 (Function Calling, Tool Use)
├── 🔲 멀티 스텝 추론
└── 🔲 사용자별 통계/분석
```

### 현재 한계

| 한계            | 설명                                      |
|---------------|-----------------------------------------|
| Vector DB 미구현 | RAG는 파일 첨부 형식만 지원, 벡터 검색 미지원            |
| 단일 모델         | 개발 PC 성능 이슈로 경량 모델만 사용                  |
| 에이전틱 미지원      | Function Calling, Tool Use 등 에이전트 기능 없음 |

---

<p align="center">
  Made with ☕ and Spring AI
</p>
