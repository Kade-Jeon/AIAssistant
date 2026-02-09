package com.kade.AIAssistant.feature.conversation.service;

import com.kade.AIAssistant.feature.conversation.dto.request.AssistantRequest;
import com.kade.AIAssistant.feature.conversation.dto.response.UserConversationItemDto;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 스트리밍을 위한 대화(Conversation) 초기화 전담 컴포넌트.
 *
 * <p>책임 (Single Responsibility):
 * <ul>
 *   <li>새 대화 vs 기존 대화 판단</li>
 *   <li>새 대화 시 제목 결정 (요청 제목 또는 AI 요약)</li>
 *   <li>유저-대화 매핑 등록/갱신 ({@link UserConversationEnsureService} 위임)</li>
 *   <li>새 대화일 때만 SSE {@code conversation_created} 이벤트 발송</li>
 * </ul>
 *
 * <p>사용자 메시지 저장, Idempotency claim 등은 담당하지 않으며,
 * {@link ConversationService}에서 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationInitializer {

    private final UserConversationEnsureService userConversationEnsureService;
    private final ModelExecuteService modelExecuteService;

    /** 제목 최대 길이 (DB/클라이언트 일관성) */
    private static final int SUBJECT_MAX_LENGTH = 32;

    /**
     * 스트리밍을 위해 대화를 초기화한다.
     *
     * <p>동작:
     * <ul>
     *   <li>새 대화({@code request.conversationId()} 없음): 제목 결정 → DB ensure → SSE {@code conversation_created} 발송</li>
     *   <li>기존 대화: DB ensure만 수행 (제목 "(제목 없음)"으로 touch)</li>
     * </ul>
     *
     * @param userId         사용자 ID
     * @param conversationId 사용할 대화 ID (이미 결정된 값)
     * @param request        요청 (conversationId 유무로 새/기존 판단, subject/question으로 제목 결정)
     * @param emitter        SSE 이벤트 발송 대상 (새 대화일 때만 사용)
     */
    public void initialize(
            String userId,
            String conversationId,
            AssistantRequest request,
            SseEmitter emitter
    ) {
        boolean isNewConversation = !StringUtils.hasText(request.conversationId());

        if (isNewConversation) {
            String subject = resolveSubjectForNew(request);
            ensureOnly(userId, conversationId, subject);
            sendConversationCreated(emitter, conversationId, subject);
        } else {
            ensureOnly(userId, conversationId, "(제목 없음)");
        }
    }

    /**
     * 유저-대화 매핑만 등록/갱신한다. (SSE 이벤트 없음)
     *
     * <p>재시도 등으로 이미 대화가 있고, 제목 갱신 없이 매핑만 확보할 때 사용한다.
     *
     * @param userId         사용자 ID
     * @param conversationId 대화 ID
     * @param subject        대화 제목 (일반적으로 "(제목 없음)")
     */
    public void ensureOnly(String userId, String conversationId, String subject) {
        userConversationEnsureService.ensure(userId, conversationId, subject);
    }

    /**
     * 새 대화용 제목 결정.
     *
     * <p>우선순위: 요청의 subject → question 기반 AI 요약 → "(제목 없음)".
     * 32자 초과 시 잘라낸다.
     */
    private String resolveSubjectForNew(AssistantRequest request) {
        if (StringUtils.hasText(request.subject())) {
            String s = request.subject();
            return s.length() > SUBJECT_MAX_LENGTH ? s.substring(0, SUBJECT_MAX_LENGTH) : s;
        }
        if (StringUtils.hasText(request.question())) {
            String generated = modelExecuteService.generateConversationSubject(request.question());
            return StringUtils.hasText(generated) ? generated : "(제목 없음)";
        }
        return "(제목 없음)";
    }

    /**
     * SSE로 {@code conversation_created} 이벤트를 전송한다.
     * 전송 실패 시 로그만 남기고 예외를 밖으로 전파하지 않는다.
     */
    private void sendConversationCreated(SseEmitter emitter, String conversationId, String subject) {
        try {
            emitter.send(SseEmitter.event()
                    .name("conversation_created")
                    .data(new UserConversationItemDto(conversationId, subject)));
        } catch (IOException e) {
            log.warn("conversation_created 이벤트 전송 실패 - conversationId: {}", conversationId, e);
        }
    }
}
