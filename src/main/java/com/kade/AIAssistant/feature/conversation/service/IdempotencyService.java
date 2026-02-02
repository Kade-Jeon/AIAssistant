package com.kade.AIAssistant.feature.conversation.service;

import com.kade.AIAssistant.common.exceptions.customs.IdempotencyConflictException;
import com.kade.AIAssistant.infra.redis.enums.RedisKeyPrefix;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Idempotency-Key 기반 중복 요청 방지.
 * Redis에 상태(IN_PROGRESS / COMPLETED / FAILED)를 저장하고, 재시도 시 사용자 메시지 중복 저장을 방지한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.idempotency.ttl-hours:24}")
    private int ttlHours;

    private static final Duration RETRY_LOCK_TTL = Duration.ofMinutes(5);

    private static String stateKey(String userId, String idempotencyKey) {
        return RedisKeyPrefix.IDEMPOTENCY + ":" + userId + ":" + idempotencyKey;
    }

    private static String retryLockKey(String userId, String idempotencyKey) {
        return RedisKeyPrefix.IDEMPOTENCY + ":retry_lock:" + userId + ":" + idempotencyKey;
    }

    /**
     * Idempotency-Key에 해당하는 상태 조회
     */
    public Optional<IdempotencyState> get(String userId, String idempotencyKey) {
        String key = stateKey(userId, idempotencyKey);
        Object value = redisTemplate.opsForValue().get(key);
        if (value != null && value instanceof IdempotencyState state) {
            return Optional.of(state);
        }
        return Optional.empty();
    }

    /**
     * 최초 요청 시 상태를 IN_PROGRESS로 등록(claim).
     * 이미 키가 존재하면 false 반환 (중복 요청).
     */
    public boolean claim(String userId, String idempotencyKey, String conversationId, UUID userMessageId) {
        String key = stateKey(userId, idempotencyKey);
        IdempotencyState state = IdempotencyState.builder()
                .status(IdempotencyState.IN_PROGRESS)
                .conversationId(conversationId)
                .userMessageId(userMessageId)
                .build();
        Boolean set = redisTemplate.opsForValue().setIfAbsent(key, state, Duration.ofHours(ttlHours));
        if (Boolean.TRUE.equals(set)) {
            log.debug("Idempotency claim - key: {}, conversationId: {}", idempotencyKey, conversationId);
            return true;
        }
        return false;
    }

    /**
     * 스트리밍 완료 시 COMPLETED로 갱신
     */
    public void markCompleted(String userId, String idempotencyKey) {
        String key = stateKey(userId, idempotencyKey);
        IdempotencyState existing = (IdempotencyState) redisTemplate.opsForValue().get(key);
        if (existing != null) {
            existing.setStatus(IdempotencyState.COMPLETED);
            redisTemplate.opsForValue().set(key, existing, Duration.ofHours(ttlHours));
            log.debug("Idempotency completed - key: {}", idempotencyKey);
        }
    }

    /**
     * 스트리밍 실패 시 FAILED로 갱신
     */
    public void markFailed(String userId, String idempotencyKey) {
        String key = stateKey(userId, idempotencyKey);
        IdempotencyState existing = (IdempotencyState) redisTemplate.opsForValue().get(key);
        if (existing != null) {
            existing.setStatus(IdempotencyState.FAILED);
            redisTemplate.opsForValue().set(key, existing, Duration.ofHours(ttlHours));
            log.debug("Idempotency failed - key: {}", idempotencyKey);
        }
    }

    /**
     * FAILED 상태일 때만 재시도 권한 획득. retry_lock으로 동시 재시도 방지.
     * 권한을 얻으면 상태를 IN_PROGRESS로 바꾸고 저장된 conversationId를 반환.
     * 이미 IN_PROGRESS이면 IdempotencyConflictException.
     * COMPLETED이면 IdempotencyConflictException은 아니고, 호출 측에서 "already completed" 처리.
     */
    public IdempotencyState tryStartRetry(String userId, String idempotencyKey) {
        Optional<IdempotencyState> opt = get(userId, idempotencyKey);
        if (opt.isEmpty()) {
            return null;
        }
        IdempotencyState state = opt.get();
        if (IdempotencyState.IN_PROGRESS.equals(state.getStatus())) {
            throw new IdempotencyConflictException(
                    "동일한 Idempotency-Key로 요청이 이미 처리 중입니다.",
                    IdempotencyConflictException.CODE_REQUEST_IN_PROGRESS
            );
        }
        if (IdempotencyState.COMPLETED.equals(state.getStatus())) {
            return state;
        }
        if (!IdempotencyState.FAILED.equals(state.getStatus())) {
            return null;
        }
        String lockKey = retryLockKey(userId, idempotencyKey);
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", RETRY_LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            throw new IdempotencyConflictException(
                    "동일한 Idempotency-Key로 재시도가 이미 진행 중입니다.",
                    IdempotencyConflictException.CODE_REQUEST_IN_PROGRESS
            );
        }
        String stateKey = stateKey(userId, idempotencyKey);
        state.setStatus(IdempotencyState.IN_PROGRESS);
        redisTemplate.opsForValue().set(stateKey, state, Duration.ofHours(ttlHours));
        log.debug("Idempotency retry started - key: {}, conversationId: {}", idempotencyKey, state.getConversationId());
        return state;
    }

    /**
     * 재시도 완료/실패 후 retry_lock 해제
     */
    public void releaseRetryLock(String userId, String idempotencyKey) {
        redisTemplate.delete(retryLockKey(userId, idempotencyKey));
    }
}
