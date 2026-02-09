package com.kade.AIAssistant.feature.conversation.service;

import com.kade.AIAssistant.common.enums.PromptType;
import com.kade.AIAssistant.common.exceptions.customs.ForbiddenException;
import com.kade.AIAssistant.common.exceptions.customs.IdempotencyConflictException;
import com.kade.AIAssistant.common.prompt.PromptService;
import com.kade.AIAssistant.feature.conversation.dto.request.AssistantRequest;
import com.kade.AIAssistant.feature.conversation.dto.response.AttachmentDto;
import com.kade.AIAssistant.feature.conversation.dto.response.ConversationMessageDto;
import com.kade.AIAssistant.feature.conversation.dto.response.StreamingSessionInfo;
import com.kade.AIAssistant.feature.conversation.dto.response.UserConversationItemDto;
import com.kade.AIAssistant.feature.conversation.entity.ChatAttachmentEntity;
import com.kade.AIAssistant.feature.conversation.entity.ChatMessageEntity;
import com.kade.AIAssistant.feature.conversation.entity.UserConversationEntity;
import com.kade.AIAssistant.feature.conversation.repository.ChatAttachmentRepository;
import com.kade.AIAssistant.feature.conversation.repository.ChatMessageRepository;
import com.kade.AIAssistant.feature.conversation.repository.UserConversationRepository;
import com.kade.AIAssistant.feature.conversation.service.idempotency.IdempotencyResolutionResult;
import com.kade.AIAssistant.feature.conversation.service.idempotency.StreamingIdempotencyCoordinator;
import com.kade.AIAssistant.feature.project.repository.ProjectDocumentRepository;
import com.kade.AIAssistant.feature.project.repository.UserProjectRepository;
import com.kade.AIAssistant.feature.project.service.ProjectRagService;
import com.kade.AIAssistant.infra.redis.context.RedisChatMemory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

/**
 * AI 채팅 스트리밍 오케스트레이션 서비스.
 *
 * <p>역할: 스트리밍 플로우 조정 (Facade Pattern)
 * <ul>
 *   <li>Idempotency 체크 → 대화 초기화 → 메시지 저장 → AI 실행 → 결과 저장</li>
 * </ul>
 *
 * <p>의존성이 많은 이유:
 * <ul>
 *   <li>스트리밍 하나를 완성하려면 여러 단계의 협력이 필요하기 때문</li>
 *   <li>복잡한 로직은 이미 전문 컴포넌트(Idempotency, Initializer)로 분리됨</li>
 *   <li>남은 의존성은 데이터 접근 + 실행 위임</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConversationService {

    @Value("${spring.ai.ollama.chat.model:default}")
    private String defaultModelName;

    @Value("${app.conversation.default-limit:20}")
    private int defaultLimit;

    @Value("${app.conversation.max-limit:100}")
    private int maxLimit;

    private final StreamingService streamingService;
    private final ModelExecuteService modelExecuteService;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatAttachmentRepository chatAttachmentRepository;
    private final UserConversationRepository userConversationRepository;
    private final RedisChatMemory redisChatMemory;
    private final UserProjectRepository userProjectRepository;
    private final ProjectRagService projectRagService;
    private final ProjectDocumentRepository projectDocumentRepository;
    /**
     * 대화 초기화(ensure + 제목 결정 + SSE 이벤트)는 ConversationInitializer에 위임
     */
    private final ConversationInitializer conversationInitializer;
    private final StreamingIdempotencyCoordinator idempotencyCoordinator;
    private final PromptService promptService;

    /**
     * 실제 스트리밍에 사용되는 모델명을 Langfuse config와 동일 소스에서 조회한다. ModelExecuteService와 일치시키기 위해 PromptService를 통해 조회하며, 없으면
     * application 기본값 사용.
     */
    private String resolveModelName(PromptType promptType) {
        String model = promptService.getLangfusePrompt(promptType).config().model();
        return StringUtils.hasText(model) ? model : defaultModelName;
    }

    /**
     * [SSE 스트리밍] AI 채팅 응답 생성 (Idempotency-Key 지원). X-Idempotency-Key가 있으면 동일 키로 재요청 시 사용자 메시지 중복 저장을 방지하고, 이미 완료된 요청이면
     * already_completed 이벤트로 응답한다.
     */
    @Transactional
    public String streamToSse(String userId, AssistantRequest request, SseEmitter emitter, String idempotencyKey) {
        // 1. Idempotency 처리 - StreamingIdempotencyCoordinator에 위임
        IdempotencyResolutionResult resolution = idempotencyCoordinator.resolve(
                userId, idempotencyKey, request, emitter);

        // 이미 완료된 요청인 경우 조기 반환
        if (resolution.isAlreadyCompleted()) {
            return resolution.getConversationId();
        }

        String conversationId = resolution.getConversationId();
        boolean skipSaveUserMessage = resolution.isSkipSaveUserMessage();

        // 2. Conversation 초기화 및 사용자 메시지 저장
        if (!skipSaveUserMessage) {
            initializeConversationAndSaveUserMessage(userId, conversationId, request, emitter, idempotencyKey);
        } else {
            // 재시도인 경우: 매핑만 확보 (SSE 이벤트 없음)
            conversationInitializer.ensureOnly(userId, conversationId, "(제목 없음)", request.promptType());
        }

        // 3. 요청 객체 준비 (RAG/도구는 promptType == PROJECT로 ModelExecuteService에서 처리)
        AssistantRequest requestToUse = new AssistantRequest(
                request.promptType(),
                request.question(),
                conversationId,
                request.subject()
        );

        log.info("SSE 스트리밍 시작 - conversationId: {}, 질문: {}, idempotencyKey: {}",
                conversationId, request.question(), idempotencyKey);

        // 4. 스트리밍 실행
        Flux<ChatResponse> stream = modelExecuteService.stream(userId, requestToUse);
        stream = idempotencyCoordinator.attachErrorHandler(stream, userId, idempotencyKey);

        StreamingSessionInfo sessionInfo = new StreamingSessionInfo();
        sessionInfo.setModel(resolveModelName(requestToUse.promptType()));

        // 5. 완료 콜백 설정
        final String finalConversationId = conversationId;
        Runnable saveAssistantCallback = () -> {
            saveAssistantMessage(finalConversationId, sessionInfo);
            idempotencyCoordinator.markCompleted(userId, idempotencyKey);
        };

        streamingService.streamToSse(stream, emitter, sessionInfo, saveAssistantCallback);

        return conversationId;
    }

    /**
     * Conversation 초기화(ensure + SSE) 및 사용자 메시지 저장, Idempotency claim. 초기화는 {@link ConversationInitializer}에 위임한다.
     */
    private void initializeConversationAndSaveUserMessage(
            String userId,
            String conversationId,
            AssistantRequest request,
            SseEmitter emitter,
            String idempotencyKey
    ) {
        conversationInitializer.initialize(userId, conversationId, request, emitter);

        // 사용자 메시지 저장
        saveUserMessage(conversationId, request.question());

        // Idempotency claim
        if (StringUtils.hasText(idempotencyKey)) {
            UUID userMessageId = chatMessageRepository
                    .findFirstByConversationIdAndTypeOrderByTimestampDesc(
                            conversationId,
                            com.kade.AIAssistant.common.enums.MessageType.USER
                    )
                    .map(ChatMessageEntity::getId)
                    .orElse(null);

            boolean claimed = idempotencyCoordinator.claim(userId, idempotencyKey, conversationId, userMessageId);
            if (!claimed) {
                throw new IdempotencyConflictException(
                        "동일한 Idempotency-Key로 요청이 이미 처리 중입니다.",
                        IdempotencyConflictException.CODE_REQUEST_IN_PROGRESS
                );
            }
        }
    }

    /**
     * [SSE 스트리밍] AI 채팅 응답 생성 (완료 콜백 포함). 첨부파일이 있는 경우 스트리밍 완료 후 메타데이터를 저장하기 위해 사용됩니다.
     *
     * @param userId             사용자 ID
     * @param request            요청 정보
     * @param emitter            SSE 에미터
     * @param onCompleteCallback 스트리밍 완료 시 실행할 콜백
     * @return 사용 중인 conversationId (신규 생성 포함)
     */
    @Transactional
    public String streamToSse(String userId, AssistantRequest request, SseEmitter emitter,
                              Runnable onCompleteCallback) {
        String conversationId = resolveConversationId(request);
        boolean isNewConversation = !StringUtils.hasText(request.conversationId());

        conversationInitializer.initialize(userId, conversationId, request, emitter);

        AssistantRequest requestToUse = isNewConversation
                ? new AssistantRequest(request.promptType(), request.question(), conversationId,
                request.subject())
                : request;

        log.info("SSE 스트리밍 시작 - conversationId: {}, 질문: {}", conversationId, request.question());

        // USER 메시지를 우리 테이블에 저장 (AI 호출 전)
        UUID userMessageId = saveUserMessage(conversationId, request.question());

        Flux<ChatResponse> stream = modelExecuteService.stream(userId, requestToUse);

        StreamingSessionInfo sessionInfo = new StreamingSessionInfo();
        sessionInfo.setModel(resolveModelName(requestToUse.promptType()));

        // 스트리밍 완료 후 ASSISTANT 메시지 저장 콜백 추가
        Runnable combinedCallback = () -> {
            // ASSISTANT 메시지 저장
            saveAssistantMessage(conversationId, sessionInfo);
            // 기존 콜백 실행 (첨부파일 메타데이터 저장 등)
            if (onCompleteCallback != null) {
                onCompleteCallback.run();
            }
        };

        streamingService.streamToSse(stream, emitter, sessionInfo, combinedCallback);

        return conversationId;
    }

    /**
     * [SSE 스트리밍] AI 채팅 응답 생성 (완료 콜백 포함, userMessageId 반환). 첨부파일이 있는 경우 스트리밍 완료 후 메타데이터를 저장하기 위해 사용됩니다.
     *
     * @param userId             사용자 ID
     * @param request            요청 정보
     * @param emitter            SSE 에미터
     * @param onCompleteCallback 스트리밍 완료 시 실행할 콜백 (userMessageId를 받을 수 있음)
     * @return 사용 중인 conversationId (신규 생성 포함)
     */
    @Transactional
    public String streamToSseWithUserMessageId(String userId, AssistantRequest request, SseEmitter emitter,
                                               Consumer<UUID> onCompleteCallback) {
        String conversationId = resolveConversationId(request);
        boolean isNewConversation = !StringUtils.hasText(request.conversationId());

        conversationInitializer.initialize(userId, conversationId, request, emitter);

        AssistantRequest requestToUse = isNewConversation
                ? new AssistantRequest(request.promptType(), request.question(), conversationId,
                request.subject())
                : request;

        log.info("SSE 스트리밍 시작 - conversationId: {}, 질문: {}", conversationId, request.question());

        // USER 메시지를 우리 테이블에 저장 (AI 호출 전)
        UUID userMessageId = saveUserMessage(conversationId, request.question());

        Flux<ChatResponse> stream = modelExecuteService.stream(userId, requestToUse);

        StreamingSessionInfo sessionInfo = new StreamingSessionInfo();
        sessionInfo.setModel(resolveModelName(requestToUse.promptType()));

        // 스트리밍 완료 후 ASSISTANT 메시지 저장 + 콜백 실행
        Runnable combinedCallback = () -> {
            // ASSISTANT 메시지 저장
            saveAssistantMessage(conversationId, sessionInfo);
            // 기존 콜백 실행 (userMessageId 전달)
            if (onCompleteCallback != null) {
                onCompleteCallback.accept(userMessageId);
            }
        };

        streamingService.streamToSse(stream, emitter, sessionInfo, combinedCallback);

        return conversationId;
    }

    /**
     * 특정 유저의 모든 대화 목록(conversationId, subject)을 최신순으로 반환.
     */
    public List<UserConversationItemDto> getConversations(String userId) {
        List<UserConversationEntity> list = userConversationRepository.findById_UserIdOrderByUpdatedAtDesc(
                userId, PageRequest.of(0, 500));
        return list.stream()
                .map(e -> new UserConversationItemDto(e.getId().getConversationId(), e.getSubject()))
                .toList();
    }


    /**
     * 특정 대화방의 대화 목록 조회 (페이징 지원). 해당 conversationId가 userId 소유인지 검증 후 조회.
     *
     * @param userId          사용자 ID
     * @param conversationId  대화 ID
     * @param limit           없으면 기본 20개, 있으면 해당 개수(최대 100개)만 조회
     * @param beforeTimestamp 이 시간 이전의 메시지 조회 (스크롤 업용, 선택사항)
     * @return 대화 메시지 리스트
     * @throws ForbiddenException 해당 대화에 대한 접근 권한이 없을 때
     */
    public List<ConversationMessageDto> getConversation(
            String userId, String conversationId, Integer limit, Instant beforeTimestamp) {
        boolean hasConversation = userConversationRepository.existsById_UserIdAndId_ConversationId(userId,
                conversationId);
        boolean hasProject = userProjectRepository.existsById_UserIdAndId_ConversationId(userId, conversationId);
        if (!hasConversation && !hasProject) {
            throw new ForbiddenException("해당 대화에 대한 접근 권한이 없습니다.");
        }
        int effectiveLimit = (limit == null || limit <= 0) ? defaultLimit : Math.min(limit, maxLimit);

        List<ChatMessageEntity> recent;
        if (beforeTimestamp != null) {
            // 페이징 조회 (스크롤 업)
            PageRequest pageRequest = PageRequest.of(0, effectiveLimit);
            recent = chatMessageRepository.findByConversationIdAndTimestampBefore(
                    conversationId, beforeTimestamp, pageRequest);
            log.info("페이징 조회 - conversationId: {}, beforeTimestamp: {}, limit: {}",
                    conversationId, beforeTimestamp, effectiveLimit);
        } else {
            // 최신 메시지 조회
            PageRequest pageRequest = PageRequest.of(0, effectiveLimit);
            recent = chatMessageRepository.findRecentByConversationId(conversationId, pageRequest);
        }

        // 첨부파일 조회 (message_id별 그룹화)
        List<ChatAttachmentEntity> allAttachments = chatAttachmentRepository
                .findByConversationIdOrderByCreatedAtDesc(conversationId);

        log.info("대화 조회 - userId: {}, conversationId: {}, 메시지 수: {}, 첨부파일 수: {}",
                userId, conversationId, recent.size(), allAttachments.size());

        Map<UUID, List<AttachmentDto>> attachmentsByMessageId = allAttachments.stream()
                .collect(Collectors.groupingBy(
                        ChatAttachmentEntity::getMessageId,
                        Collectors.mapping(
                                a -> new AttachmentDto(a.getFilename(), a.getMimeType(), a.getSize(), a.getCreatedAt()),
                                Collectors.toList()
                        )
                ));

        List<ConversationMessageDto> result = new ArrayList<>(recent.size());
        List<Message> messagesForCache = new ArrayList<>(recent.size());
        for (int i = recent.size() - 1; i >= 0; i--) {
            ChatMessageEntity e = recent.get(i);
            UUID messageId = e.getId();
            if (messageId == null) {
                log.warn("메시지 id가 null입니다 - conversationId: {}, timestamp: {}, type: {}",
                        conversationId, e.getTimestamp(), e.getType());
            }
            List<AttachmentDto> attachments = messageId != null
                    ? attachmentsByMessageId.getOrDefault(messageId, Collections.emptyList())
                    : Collections.emptyList();

            result.add(new ConversationMessageDto(
                    e.getType().getValue(),
                    e.getContent(),
                    e.getTimestamp(),
                    attachments.isEmpty() ? null : attachments));
            messagesForCache.add(toChatMemoryMessage(e));
        }
        // Redis 캐시 갱신 (페이징 조회인 경우 병합)
        if (beforeTimestamp != null) {
            // 페이징 조회 결과를 Redis 캐시에 병합
            redisChatMemory.getWithPaging(conversationId, beforeTimestamp, effectiveLimit);
        } else {
            // 최신 메시지 조회 결과로 캐시 워밍업
            redisChatMemory.warmCache(conversationId, messagesForCache);
        }

        return result;
    }

    /**
     * 특정 대화방의 대화 목록 조회 (기본 메서드, 페이징 없음). 해당 conversationId가 userId 소유인지 검증 후 조회.
     *
     * @param userId         사용자 ID
     * @param conversationId 대화 ID
     * @param limit          없으면 기본 20개, 있으면 해당 개수(최대 100개)만 조회
     * @return 대화 메시지 리스트
     * @throws ForbiddenException 해당 대화에 대한 접근 권한이 없을 때
     */
    public List<ConversationMessageDto> getConversation(String userId, String conversationId, Integer limit) {
        return getConversation(userId, conversationId, limit, null);
    }

    private static Message toChatMemoryMessage(ChatMessageEntity e) {
        com.kade.AIAssistant.common.enums.MessageType type = e.getType();
        String text = e.getContent();

        // Spring AI MessageType으로 변환
        org.springframework.ai.chat.messages.MessageType springAiType =
                org.springframework.ai.chat.messages.MessageType.fromValue(type.getValue());

        return switch (springAiType) {
            case SYSTEM -> new SystemMessage(text);
            case USER -> new UserMessage(text);
            case ASSISTANT -> new AssistantMessage(text);
            default -> new AssistantMessage(text);
        };
    }

    /**
     * USER 메시지를 우리 테이블에 저장
     *
     * @return 저장된 메시지의 messageId (저장 실패 시 null)
     */
    @Transactional(readOnly = false)
    private UUID saveUserMessage(String conversationId, String userQuestion) {
        if (!StringUtils.hasText(userQuestion)) {
            return null;
        }

        // 파일 첨부 형식인 경우 "사용자 요청:" 이후만 저장
        String contentToStore = extractUserRequestFromFileAttachment(userQuestion);

        ChatMessageEntity entity = new ChatMessageEntity(
                conversationId,
                com.kade.AIAssistant.common.enums.MessageType.USER,
                contentToStore,
                Instant.now()
        );
        ChatMessageEntity saved = chatMessageRepository.save(entity);
        log.debug("USER 메시지 저장 완료 - conversationId: {}, id: {}",
                conversationId, saved.getId());
        return saved.getId();
    }

    /**
     * ASSISTANT 메시지를 우리 테이블에 저장
     */
    @Transactional(readOnly = false)
    private void saveAssistantMessage(String conversationId, StreamingSessionInfo sessionInfo) {
        String content = sessionInfo.getAccumulatedContent();
        if (!StringUtils.hasText(content)) {
            log.debug("ASSISTANT 메시지 content가 비어있어 저장하지 않음 - conversationId: {}", conversationId);
            return;
        }

        ChatMessageEntity entity = new ChatMessageEntity(
                conversationId,
                com.kade.AIAssistant.common.enums.MessageType.ASSISTANT,
                content,
                Instant.now()
        );
        chatMessageRepository.save(entity);
        log.info("ASSISTANT 메시지 저장 완료 - conversationId: {}, id: {}, content 길이: {}",
                conversationId, entity.getId(), content.length());
    }

    /**
     * 파일 첨부 형식에서 사용자 요청 부분만 추출
     */
    private static String extractUserRequestFromFileAttachment(String content) {
        String FILE_ATTACHMENT_MARKER = "다음 첨부파일(문서) 내용:";
        String USER_REQUEST_MARKER = "사용자 요청:";

        if (!content.startsWith(FILE_ATTACHMENT_MARKER) || !content.contains(USER_REQUEST_MARKER)) {
            return content;
        }
        int idx = content.indexOf(USER_REQUEST_MARKER);
        String userRequest = content.substring(idx + USER_REQUEST_MARKER.length()).trim();
        return StringUtils.hasText(userRequest) ? userRequest : content;
    }

    /**
     * 파일 첨부 메타데이터 저장. 저장된 USER 메시지의 message_id를 직접 사용하여 연결.
     * <p>
     * <b>주의:</b> 이 메서드는 첨부파일이 있는 경우에만 호출되어야 합니다.
     * 일반 메시지(첨부파일 없음)의 경우 호출되지 않습니다.
     * </p>
     *
     * @param conversationId 대화 ID
     * @param userMessageId  저장된 USER 메시지의 messageId (직접 전달)
     * @param filename       파일명
     * @param mimeType       MIME 타입
     * @param size           파일 크기 (bytes)
     */
    @Transactional(readOnly = false)
    public void saveAttachmentMetadata(String conversationId, UUID userMessageId, String filename, String mimeType,
                                       Long size) {
        if (userMessageId == null) {
            log.warn("파일 첨부 메타데이터 저장 실패: userMessageId가 null - conversationId: {}", conversationId);
            return;
        }

        log.info("파일 첨부 메타데이터 저장 시작 - conversationId: {}, messageId: {}, filename: {}",
                conversationId, userMessageId, filename);

        // ChatMessageEntity 조회
        ChatMessageEntity messageEntity = chatMessageRepository.findById(userMessageId)
                .orElseThrow(() -> new RuntimeException("메시지를 찾을 수 없습니다: " + userMessageId));

        ChatAttachmentEntity attachment = new ChatAttachmentEntity(
                messageEntity, conversationId, filename, mimeType, size);
        ChatAttachmentEntity saved = chatAttachmentRepository.save(attachment);
        log.info("파일 첨부 메타데이터 저장 완료 - conversationId: {}, messageId: {}, filename: {}, savedId: {}",
                conversationId, userMessageId, filename, saved.getId());
    }

    @Transactional(readOnly = false)
    public boolean deleteConversation(String userId, String conversationId) {
        boolean hasConversation = userConversationRepository.findById_UserIdAndId_ConversationId(userId, conversationId)
                .isPresent();
        boolean hasProject = userProjectRepository.existsById_UserIdAndId_ConversationId(userId, conversationId);

        if (!hasConversation && !hasProject) {
            return false;
        }

        chatAttachmentRepository.deleteByConversationId(conversationId);
        redisChatMemory.clear(conversationId);
        userConversationRepository.deleteById_UserIdAndId_ConversationId(userId, conversationId);

        if (hasProject) {
            projectRagService.deleteByProject(userId, conversationId);
            projectDocumentRepository.deleteByConversationIdAndUserId(conversationId, userId);
            userProjectRepository.deleteById_UserIdAndId_ConversationId(userId, conversationId);
        }
        return true;
    }

    /**
     * 대화 제목 변경. 소유자가 아니면 {@link ForbiddenException} 발생.
     */
    @Transactional(readOnly = false)
    public void changeSubject(String userId, String conversationId, String subject) {
        UserConversationEntity convEntity = userConversationRepository
                .findById_UserIdAndId_ConversationId(userId, conversationId)
                .orElse(null);
        var projectEntity = userProjectRepository.findById_UserIdAndId_ConversationId(userId, conversationId);

        if (convEntity == null && projectEntity.isEmpty()) {
            throw new ForbiddenException("해당 대화에 대한 접근 권한이 없습니다.");
        }

        String s = subject != null ? subject : "(제목 없음)";
        String trimmedSubject = s.length() > 32 ? s.substring(0, 32) : s;

        if (convEntity != null) {
            convEntity.changeSubject(trimmedSubject);
            userConversationRepository.save(convEntity);
        }
        projectEntity.ifPresent(e -> {
            e.changeSubject(trimmedSubject);
            userProjectRepository.save(e);
        });
    }

    /**
     * conversationId 없으면 새 UUID. (promptType == PROJECT 시 RAG/도구는 ModelExecuteService에서 처리)
     */
    private String resolveConversationId(AssistantRequest request) {
        return StringUtils.hasText(request.conversationId())
                ? request.conversationId()
                : UUID.randomUUID().toString();
    }
}
