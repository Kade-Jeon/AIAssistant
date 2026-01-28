package com.kade.AIAssistant.feature.conversation.service;

import com.kade.AIAssistant.common.exceptions.customs.ForbiddenException;
import com.kade.AIAssistant.domain.reqeust.AssistantRequest;
import com.kade.AIAssistant.domain.response.AttachmentDto;
import com.kade.AIAssistant.domain.response.ConversationMessageDto;
import com.kade.AIAssistant.domain.response.StreamingSessionInfo;
import com.kade.AIAssistant.domain.response.UserConversationItemDto;
import com.kade.AIAssistant.feature.conversation.entity.ChatAttachmentEntity;
import com.kade.AIAssistant.feature.conversation.entity.ChatMessageEntity;
import com.kade.AIAssistant.feature.conversation.entity.UserConversationEntity;
import com.kade.AIAssistant.feature.conversation.repository.ChatAttachmentRepository;
import com.kade.AIAssistant.feature.conversation.repository.ChatMessageRepository;
import com.kade.AIAssistant.feature.conversation.repository.UserConversationRepository;
import com.kade.AIAssistant.infra.redis.context.RedisChatMemory;
import java.io.IOException;
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
import org.springframework.ai.chat.messages.MessageType;
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
 * AI 채팅 서비스 (핵심 비즈니스 로직) - SOLID 원칙 준수: 각 책임을 전문 컴포넌트에 위임
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConversationService {

    @Value("${spring.ai.ollama.chat.model:default}")
    private String MODEL_NAME;

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
    private final UserConversationEnsureService userConversationEnsureService;

    /**
     * [SSE 스트리밍] AI 채팅 응답 생성. conversationId가 비어 있으면 UUID로 새로 생성해 DB에 등록하고, 그 id로 대화를 이어간다.
     *
     * @return 사용 중인 conversationId (신규 생성 포함)
     */
    @Transactional
    public String streamToSse(String userId, AssistantRequest request, SseEmitter emitter) {
        boolean isNewConversation = !StringUtils.hasText(request.conversationId());
        String conversationId = isNewConversation
                ? UUID.randomUUID().toString()
                : request.conversationId();

        if (isNewConversation) {
            String subject = resolveSubjectForNew(request);
            userConversationEnsureService.ensure(userId, conversationId, subject);
            try {
                emitter.send(SseEmitter.event()
                        .name("conversation_created")
                        .data(new UserConversationItemDto(conversationId, subject)));
            } catch (IOException e) {
                log.warn("conversation_created 이벤트 전송 실패", e);
            }
        } else {
            userConversationEnsureService.ensure(userId, conversationId, "(제목 없음)");
        }

        AssistantRequest requestToUse = isNewConversation
                ? new AssistantRequest(request.promptType(), request.question(), request.language(), conversationId,
                request.subject())
                : request;

        log.info("SSE 스트리밍 시작 - conversationId: {}, 질문: {}", conversationId, request.question());

        // USER 메시지를 우리 테이블에 저장 (AI 호출 전)
        saveUserMessage(conversationId, request.question());

        Flux<ChatResponse> stream = modelExecuteService.stream(requestToUse);

        StreamingSessionInfo sessionInfo = new StreamingSessionInfo();
        sessionInfo.setModel(MODEL_NAME);

        // 스트리밍 완료 후 ASSISTANT 메시지 저장 콜백 추가
        Runnable saveAssistantCallback = () -> {
            saveAssistantMessage(conversationId, sessionInfo);
        };

        streamingService.streamToSse(stream, emitter, sessionInfo, saveAssistantCallback);

        return conversationId;
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
        boolean isNewConversation = !StringUtils.hasText(request.conversationId());
        String conversationId = isNewConversation
                ? UUID.randomUUID().toString()
                : request.conversationId();

        if (isNewConversation) {
            String subject = resolveSubjectForNew(request);
            userConversationEnsureService.ensure(userId, conversationId, subject);
            try {
                emitter.send(SseEmitter.event()
                        .name("conversation_created")
                        .data(new UserConversationItemDto(conversationId, subject)));
            } catch (IOException e) {
                log.warn("conversation_created 이벤트 전송 실패", e);
            }
        } else {
            userConversationEnsureService.ensure(userId, conversationId, "(제목 없음)");
        }

        AssistantRequest requestToUse = isNewConversation
                ? new AssistantRequest(request.promptType(), request.question(), request.language(), conversationId,
                request.subject())
                : request;

        log.info("SSE 스트리밍 시작 - conversationId: {}, 질문: {}", conversationId, request.question());

        // USER 메시지를 우리 테이블에 저장 (AI 호출 전)
        UUID userMessageId = saveUserMessage(conversationId, request.question());

        Flux<ChatResponse> stream = modelExecuteService.stream(requestToUse);

        StreamingSessionInfo sessionInfo = new StreamingSessionInfo();
        sessionInfo.setModel(MODEL_NAME);

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
        boolean isNewConversation = !StringUtils.hasText(request.conversationId());
        String conversationId = isNewConversation
                ? UUID.randomUUID().toString()
                : request.conversationId();

        if (isNewConversation) {
            String subject = resolveSubjectForNew(request);
            userConversationEnsureService.ensure(userId, conversationId, subject);
            try {
                emitter.send(SseEmitter.event()
                        .name("conversation_created")
                        .data(new UserConversationItemDto(conversationId, subject)));
            } catch (IOException e) {
                log.warn("conversation_created 이벤트 전송 실패", e);
            }
        } else {
            userConversationEnsureService.ensure(userId, conversationId, "(제목 없음)");
        }

        AssistantRequest requestToUse = isNewConversation
                ? new AssistantRequest(request.promptType(), request.question(), request.language(), conversationId,
                request.subject())
                : request;

        log.info("SSE 스트리밍 시작 - conversationId: {}, 질문: {}", conversationId, request.question());

        // USER 메시지를 우리 테이블에 저장 (AI 호출 전)
        UUID userMessageId = saveUserMessage(conversationId, request.question());

        Flux<ChatResponse> stream = modelExecuteService.stream(requestToUse);

        StreamingSessionInfo sessionInfo = new StreamingSessionInfo();
        sessionInfo.setModel(MODEL_NAME);

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
     * 새 대화용 제목 결정. 넘어온 제목이 있으면 사용하고, 없으면 AI 요약 후 저장에 쓴다. 새 대화에서만 호출한다.
     */
    private String resolveSubjectForNew(AssistantRequest request) {
        if (StringUtils.hasText(request.subject())) {
            String s = request.subject();
            return s.length() > 32 ? s.substring(0, 32) : s;
        }
        if (StringUtils.hasText(request.question())) {
            String generated = modelExecuteService.generateConversationSubject(request.question());
            return StringUtils.hasText(generated) ? generated : "(제목 없음)";
        }
        return "(제목 없음)";
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
        if (!userConversationRepository.existsById_UserIdAndId_ConversationId(userId, conversationId)) {
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

        log.info("대화 조회 - conversationId: {}, 메시지 수: {}, 첨부파일 수: {}",
                conversationId, recent.size(), allAttachments.size());

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
            UUID messageId = e.getMessageId();
            if (messageId == null) {
                log.warn("메시지 message_id가 null입니다 - conversationId: {}, timestamp: {}, type: {}",
                        conversationId, e.getTimestamp(), e.getType());
            }
            List<AttachmentDto> attachments = messageId != null
                    ? attachmentsByMessageId.getOrDefault(messageId, Collections.emptyList())
                    : Collections.emptyList();

            result.add(new ConversationMessageDto(
                    e.getType().toLowerCase(),
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
        // Spring AI MessageType.fromValue()는 소문자("user","assistant" 등)를 기대함. DB는 대문자 저장.
        MessageType type = MessageType.fromValue(e.getType().toLowerCase());
        String text = e.getContent();
        return switch (type) {
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
                "USER",
                contentToStore,
                Instant.now()
        );
        ChatMessageEntity saved = chatMessageRepository.save(entity);
        log.debug("USER 메시지 저장 완료 - conversationId: {}, messageId: {}",
                conversationId, saved.getMessageId());
        return saved.getMessageId();
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
                "ASSISTANT",
                content,
                Instant.now()
        );
        chatMessageRepository.save(entity);
        log.info("ASSISTANT 메시지 저장 완료 - conversationId: {}, messageId: {}, content 길이: {}",
                conversationId, entity.getMessageId(), content.length());
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

        ChatAttachmentEntity attachment = new ChatAttachmentEntity(
                userMessageId, conversationId, filename, mimeType, size);
        ChatAttachmentEntity saved = chatAttachmentRepository.save(attachment);
        log.info("파일 첨부 메타데이터 저장 완료 - conversationId: {}, messageId: {}, filename: {}, savedId: {}",
                conversationId, userMessageId, filename, saved.getId());
    }

    @Transactional(readOnly = false)
    public boolean deleteConversation(String userId, String conversationId) {
        if (userConversationRepository.findById_UserIdAndId_ConversationId(userId, conversationId).isEmpty()) {
            return false;
        }
        chatAttachmentRepository.deleteByConversationId(conversationId);
        chatMessageRepository.deleteByConversationId(conversationId);
        userConversationRepository.deleteById_UserIdAndId_ConversationId(userId, conversationId);
        return true;
    }

    /**
     * 대화 제목 변경. 소유자가 아니면 {@link ForbiddenException} 발생.
     */
    @Transactional(readOnly = false)
    public void changeSubject(String userId, String conversationId, String subject) {
        UserConversationEntity entity = userConversationRepository
                .findById_UserIdAndId_ConversationId(userId, conversationId)
                .orElseThrow(() -> new ForbiddenException("해당 대화에 대한 접근 권한이 없습니다."));
        String s = subject != null ? subject : "(제목 없음)";
        entity.changeSubject(s.length() > 32 ? s.substring(0, 32) : s);
        userConversationRepository.save(entity);
    }
}
