# RAG 관련 서비스 역할 구분

세 클래스는 **계층/관점**이 다릅니다.

## ID 통일: project_id = conversation_id

프로젝트의 project_id는 conversation_id와 동일한 UUID를 사용한다. 프로젝트 생성 시 USER_PROJECT와 USER_CONVERSATION에 모두 등록하여, CHAT_MESSAGE/CHAT_ATTACHMENT와 통합된 대화 흐름을 지원한다.

---

## 1. ProjectService (`feature/project/service`)

**관점: 프로젝트 도메인 진입점 (API/유스케이스)**

| 역할           | 설명                                                                         |
| -------------- | ---------------------------------------------------------------------------- |
| 파일 벡터 저장 | 사용자 요청 → `ProjectRagService.addDocument()` 호출 + 문서 목록 테이블 등록 |
| 문서 목록 반환 | `projectId`/`userId`로 사용자가 저장한 문서 파일명 목록 반환                 |

**누가 호출:** Controller.  
**의존:** ProjectRagService, ProjectDocumentRepository, UserProjectRepository

- **“무엇을 할지”**만 결정하고, RAG/벡터의 **구체 구현은 하지 않음**. RAG 컨텍스트는 RagService가 담당.

---

## 2. ProjectRagService (`feature/project/service`)

**관점: RAG 기술 구현 (벡터 저장·검색)**

| 역할      | 설명                                                                                                    |
| --------- | ------------------------------------------------------------------------------------------------------- |
| 문서 추가 | 파일 → 텍스트 추출 → 청킹 → 임베딩 → **VectorStore에 저장** (메타데이터: project_id, user_id, filename) |
| 검색      | `userId`/`projectId`로 **유사도 검색** (List<Document> 또는 AI용 문자열)                                |

**누가 호출:** ProjectService, ContextualRagTools  
**의존:** VectorStore, DocumentService, TokenTextSplitter, UserProjectRepository(권한 검사)

- **항상 `userId`, `projectId`를 인자로 받음.**
- “지금 대화” 같은 개념 없음. 호출하는 쪽이 프로젝트/사용자를 지정.

---

## 3. RagService (`agent/service`)

**관점: 에이전트/대화 컨텍스트 + 검색 진입점**

| 역할                 | 설명                                                                                                |
| -------------------- | --------------------------------------------------------------------------------------------------- |
| 컨텍스트 보관        | **ThreadLocal**에 “지금 이 요청의 userId, projectId” 저장·제거                                      |
| 검색 (컨텍스트 기반) | **query만** 받아서, 저장된 컨텍스트의 userId/projectId로 `ProjectRagService.searchAsContext()` 호출 |

**누가 호출:** **ModelExecuteService**(setContext/clearContext), **RagTools**(searchDocuments)  
**의존:** ProjectRagService

- **RagTools**는 “질문(query)”만 알면 됨.
- “어느 프로젝트에서 검색할지”는 **스트리밍 시작 전** ModelExecuteService → RagService.setContext()로 이미 정해져 있음.

---

## 호출 흐름 요약

```
[파일 업로드]
Controller → ProjectService.addDocument()
           → ProjectRagService.addDocument()  (벡터 저장)
           → ProjectDocumentRepository.save() (문서 목록 등록)

[채팅 스트리밍 시작 (projectId 있음)]
ModelExecuteService → agentToolProvider.getTools(userId, projectId)  // ContextualRagTools 생성 (컨텍스트 포함)

[LLM이 도구 호출: "문서 검색해줘"]
ContextualRagTools.searchProjectDocuments(query)
  → ProjectRagService.searchAsContext(userId, projectId, query)  // 생성자로 받은 컨텍스트 사용
  → VectorStore 유사도 검색 → 문자열 반환
```

---

## 한 줄로 구분

| 서비스                | 한 줄 요약                                                                                          |
| --------------------- | --------------------------------------------------------------------------------------------------- |
| **ProjectService**    | 프로젝트 도메인 진입점: “파일 저장해줘”, “문서 목록 줘” (컨텍스트는 ModelExecuteService→RagService) |
| **ProjectRagService** | RAG 구현: “이 userId/projectId로 벡터 저장/검색 해줘” (항상 식별자 명시)                            |
| **RagService**        | 대화 단위 컨텍스트 + 검색: “지금 이 요청의 프로젝트”를 기억하고, query만 받아서 검색해 줌           |
