package com.kade.AIAssistant.infra.redis.prompt;

import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PromptCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Key-Value 설정
     *
     * @param key   저장할 Key
     * @param value 저장할 Value
     */
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
        log.debug("Redis Set - Key: {}, Value: {}", key, value);
    }

    /**
     * Key-Value 설정 (만료 시간 포함)
     *
     * @param key      저장할 Key
     * @param value    저장할 Value
     * @param duration 만료 시간
     */
    public void set(String key, Object value, Duration duration) {
        redisTemplate.opsForValue().set(key, value, duration);
        log.debug("Redis Set with Duration - Key: {}, Value: {}, Duration: {}", key, value, duration);
    }

    /**
     * 값 조회
     *
     * @param key 조회할 Key
     * @return 저장된 값 (없으면 Optional.empty)
     */
    public Optional<Object> get(String key) {
        Object value = redisTemplate.opsForValue().get(key);
        log.debug("Redis Get - Key: {}, Value: {}", key, value);
        return Optional.ofNullable(value);
    }

    /**
     * 특정 타입으로 값 조회
     *
     * @param key  조회할 Key
     * @param type 반환받을 타입 클래스
     * @param <T>  반환 타입
     * @return 저장된 값 (없거나 타입 불일치 시 Optional.empty)
     */
    public <T> Optional<T> get(String key, Class<T> type) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value != null && type.isInstance(value)) {
            return Optional.of(type.cast(value));
        }
        return Optional.empty();
    }

    /**
     * Key 삭제
     *
     * @param key 삭제할 Key
     * @return 삭제 여부
     */
    public boolean delete(String key) {
        Boolean result = redisTemplate.delete(key);
        log.debug("Redis Delete - Key: {}, Result: {}", key, result);
        return Boolean.TRUE.equals(result);
    }

    /**
     * Key 존재 여부 확인
     *
     * @param key 확인할 Key
     * @return 존재 여부
     */
    public boolean hasKey(String key) {
        Boolean result = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(result);
    }
}