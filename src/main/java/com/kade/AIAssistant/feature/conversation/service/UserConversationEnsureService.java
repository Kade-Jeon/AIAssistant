package com.kade.AIAssistant.feature.conversation.service;

import com.kade.AIAssistant.feature.conversation.entity.UserConversationEntity;
import com.kade.AIAssistant.feature.conversation.repository.UserConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 유저-대화 매핑 등록/갱신을 트랜잭션으로 수행.
 */
@Service
@RequiredArgsConstructor
public class UserConversationEnsureService {

    private final UserConversationRepository userConversationRepository;

    /**
     * (userId, conversationId, subject) 매핑이 없으면 등록. 이미 있으면 touch() 후 save로 updated_at만 갱신.
     */
    @Transactional
    public void ensure(String userId, String conversationId, String subject) {
        userConversationRepository.findById_UserIdAndId_ConversationId(userId, conversationId)
                .ifPresentOrElse(
                        e -> {
                            e.touch();
                            userConversationRepository.save(e);
                        },
                        () -> userConversationRepository.save(new UserConversationEntity(userId, conversationId, subject))
                );
    }
}
