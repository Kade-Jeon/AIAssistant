package com.kade.AIAssistant.feature.conversation.service;

import com.kade.AIAssistant.common.exceptions.customs.ForbiddenException;
import com.kade.AIAssistant.domain.reqeust.AssistantRequest;
import com.kade.AIAssistant.domain.response.ConversationMessageDto;
import com.kade.AIAssistant.domain.response.StreamingSessionInfo;
import com.kade.AIAssistant.domain.response.UserConversationItemDto;
import com.kade.AIAssistant.feature.conversation.entity.ChatMessageEntity;
import com.kade.AIAssistant.feature.conversation.entity.UserConversationEntity;
import com.kade.AIAssistant.feature.conversation.repository.ChatMessageRepository;
import com.kade.AIAssistant.feature.conversation.repository.UserConversationRepository;
import com.kade.AIAssistant.infra.redis.context.RedisChatMemory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
    private final UserConversationRepository userConversationRepository;
    private final RedisChatMemory redisChatMemory;
    private final UserConversationEnsureService userConversationEnsureService;

    /**
     * [SSE 스트리밍] AI 채팅 응답 생성. conversationId가 비어 있으면 UUID로 새로 생성해 DB에 등록하고, 그 id로 대화를 이어간다.
     *
     * @param userId  USER-ID 헤더 값 (대화 소유 등록용)
     * @param request 사용자 요청
     * @param emitter SSE Emitter
     */
    @Transactional
    public void streamToSse(String userId, AssistantRequest request, SseEmitter emitter) {
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
            userConversationEnsureService.ensure(userId, conversationId, "(제목 없음)"); // 기존 대화는 저장 스킵 시 사용 안 함
        }

        AssistantRequest requestToUse = isNewConversation
                ? new AssistantRequest(request.promptType(), request.question(), request.language(), conversationId,
                request.subject())
                : request;

        log.info("SSE 스트리밍 시작 - conversationId: {}, 질문: {}", conversationId, request.question());
        Flux<ChatResponse> stream = modelExecuteService.stream(requestToUse);

        // 세션 정보 초기화
        StreamingSessionInfo sessionInfo = new StreamingSessionInfo();
        sessionInfo.setModel(MODEL_NAME);

        // SSE 스트리밍 처리 (위임)
        streamingService.streamToSse(stream, emitter, sessionInfo);
    }

    /**
     * 새 대화용 제목 결정. 넘어온 제목이 있으면 사용하고, 없으면 AI 요약 후 저장에 쓴다. 새 대화에서만 호출한다.
     */
    private String resolveSubjectForNew(AssistantRequest request) {
        if (StringUtils.hasText(request.subject())) {
            return request.subject();
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

        List<ConversationMessageDto> result = new ArrayList<>(recent.size());
        List<Message> messagesForCache = new ArrayList<>(recent.size());
        for (int i = recent.size() - 1; i >= 0; i--) {
            ChatMessageEntity e = recent.get(i);
            result.add(new ConversationMessageDto(
                    e.getType().toLowerCase(),
                    e.getContent(),
                    e.getId().getTimestamp()));
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

    @Transactional(readOnly = false)
    public boolean deleteConversation(String userId, String conversationId) {
        if (userConversationRepository.findById_UserIdAndId_ConversationId(userId, conversationId).isEmpty()) {
            return false;
        }
        chatMessageRepository.deleteById_ConversationId(conversationId);
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
        entity.changeSubject(subject);
        userConversationRepository.save(entity);
    }
}
