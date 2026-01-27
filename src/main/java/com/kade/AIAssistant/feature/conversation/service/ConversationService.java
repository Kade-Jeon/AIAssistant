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
import java.util.Optional;
import java.util.UUID;
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

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

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
        Flux<ChatResponse> stream = modelExecuteService.stream(requestToUse);

        StreamingSessionInfo sessionInfo = new StreamingSessionInfo();
        sessionInfo.setModel(MODEL_NAME);
        streamingService.streamToSse(stream, emitter, sessionInfo);

        return conversationId;
    }

    /**
     * [SSE 스트리밍] AI 채팅 응답 생성 (완료 콜백 포함).
     * 첨부파일이 있는 경우 스트리밍 완료 후 메타데이터를 저장하기 위해 사용됩니다.
     *
     * @param userId 사용자 ID
     * @param request 요청 정보
     * @param emitter SSE 에미터
     * @param onCompleteCallback 스트리밍 완료 시 실행할 콜백
     * @return 사용 중인 conversationId (신규 생성 포함)
     */
    @Transactional
    public String streamToSse(String userId, AssistantRequest request, SseEmitter emitter, Runnable onCompleteCallback) {
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
        Flux<ChatResponse> stream = modelExecuteService.stream(requestToUse);

        StreamingSessionInfo sessionInfo = new StreamingSessionInfo();
        sessionInfo.setModel(MODEL_NAME);
        streamingService.streamToSse(stream, emitter, sessionInfo, onCompleteCallback);

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
     * 특정 대화방의 대화 목록 조회. 해당 conversationId가 userId 소유인지 검증 후 조회.
     *
     * @param limit 없으면 기본 20개, 있으면 해당 개수(최대 100개)만 조회
     * @throws ForbiddenException 해당 대화에 대한 접근 권한이 없을 때
     */
    public List<ConversationMessageDto> getConversation(String userId, String conversationId, Integer limit) {
        if (!userConversationRepository.existsById_UserIdAndId_ConversationId(userId, conversationId)) {
            throw new ForbiddenException("해당 대화에 대한 접근 권한이 없습니다.");
        }
        int effectiveLimit = (limit == null || limit <= 0) ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        PageRequest pageRequest = PageRequest.of(0, effectiveLimit);
        List<ChatMessageEntity> recent = chatMessageRepository.findRecentByConversationId(conversationId, pageRequest);

        // 첨부파일 조회 (message_id별 그룹화)
        List<ChatAttachmentEntity> allAttachments = chatAttachmentRepository
                .findByConversationIdOrderByCreatedAtDesc(conversationId);
        
        log.info("대화 조회 - conversationId: {}, 메시지 수: {}, 첨부파일 수: {}", 
                conversationId, recent.size(), allAttachments.size());
        
        // 조회된 첨부파일 상세 로그
        for (ChatAttachmentEntity att : allAttachments) {
            log.info("첨부파일 조회됨 - id: {}, conversationId: {}, messageId: {}, filename: {}", 
                    att.getId(), att.getConversationId(), att.getMessageId(), att.getFilename());
        }
        
        // 메시지별 messageId 로그
        for (ChatMessageEntity msg : recent) {
            log.info("메시지 - messageId: {}, conversationId: {}, type: {}, timestamp: {}", 
                    msg.getMessageId(), msg.getConversationId(), msg.getType(), msg.getTimestamp());
        }
        
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
            
            if (!attachments.isEmpty()) {
                log.info("메시지에 첨부파일 매칭됨 - messageId: {}, 첨부파일 수: {}", messageId, attachments.size());
            }
            
            result.add(new ConversationMessageDto(
                    e.getType().toLowerCase(),
                    e.getContent(),
                    e.getTimestamp(),
                    attachments.isEmpty() ? null : attachments));
            messagesForCache.add(toChatMemoryMessage(e));
        }
        redisChatMemory.warmCache(conversationId, messagesForCache);
        return result;
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
     * 파일 첨부 메타데이터 저장. 저장된 USER 메시지의 content를 기반으로 정확한 message_id를 찾아서 연결.
     * <p>
     * <b>주의:</b> 이 메서드는 첨부파일이 있는 경우에만 호출되어야 합니다.
     * 일반 메시지(첨부파일 없음)의 경우 호출되지 않습니다.
     * </p>
     *
     * @param conversationId 대화 ID
     * @param userRequestText 저장된 USER 메시지의 content (파일 첨부 시 "사용자 요청:" 이후 부분만 저장됨)
     * @param filename 파일명
     * @param mimeType MIME 타입
     * @param size 파일 크기 (bytes)
     */
    @Transactional(readOnly = false)
    public void saveAttachmentMetadata(String conversationId, String userRequestText, String filename, String mimeType, Long size) {
        log.info("파일 첨부 메타데이터 저장 시작 - conversationId: {}, filename: {}, userRequestText: {}", 
                conversationId, filename, userRequestText);
        
        UUID messageId = null;
        
        // content 기반으로 정확한 메시지 찾기 시도
        if (StringUtils.hasText(userRequestText)) {
            // content의 앞부분 일부를 사용하여 검색 (LIKE 검색)
            String contentSnippet = userRequestText.length() > 50 
                    ? userRequestText.substring(0, 50) 
                    : userRequestText;
            // LIKE 검색을 위해 % 기호 추가
            String likePattern = "%" + contentSnippet + "%";
            
            Optional<ChatMessageEntity> found = chatMessageRepository
                    .findUserMessageByConversationIdAndContentSnippet(conversationId, likePattern);
            
            if (found.isPresent()) {
                messageId = found.get().getMessageId();
                log.info("content 기반으로 USER 메시지 발견 - messageId: {}, timestamp: {}", 
                        messageId, found.get().getTimestamp());
            }
        }
        
        // content 기반 검색 실패 시 최근 USER 메시지 찾기 (fallback)
        if (messageId == null) {
            log.warn("content 기반 검색 실패, 최근 USER 메시지로 fallback - conversationId: {}", conversationId);
            for (int i = 0; i < 5; i++) {
                List<ChatMessageEntity> recent = chatMessageRepository
                        .findRecentByConversationId(conversationId, PageRequest.of(0, 10));
                log.info("USER 메시지 찾기 시도 {} - conversationId: {}, 조회된 메시지 수: {}", i + 1, conversationId, recent.size());
                messageId = recent.stream()
                        .filter(m -> "USER".equalsIgnoreCase(m.getType()))
                        .findFirst()
                        .map(m -> {
                            UUID id = m.getMessageId();
                            log.info("USER 메시지 발견 - messageId: {}, timestamp: {}, content 길이: {}", 
                                    id, m.getTimestamp(), m.getContent() != null ? m.getContent().length() : 0);
                            return id;
                        })
                        .orElse(null);
                if (messageId != null) break;
                if (i < 4) {
                    try { Thread.sleep(150); } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
        
        if (messageId == null) {
            log.warn("파일 첨부 메타데이터 저장 실패: USER 메시지 없음 - conversationId: {}", conversationId);
            return;
        }
        
        ChatAttachmentEntity attachment = new ChatAttachmentEntity(
                messageId, conversationId, filename, mimeType, size);
        ChatAttachmentEntity saved = chatAttachmentRepository.save(attachment);
        log.info("파일 첨부 메타데이터 저장 완료 - conversationId: {}, messageId: {}, filename: {}, savedId: {}", 
                conversationId, messageId, filename, saved.getId());
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
