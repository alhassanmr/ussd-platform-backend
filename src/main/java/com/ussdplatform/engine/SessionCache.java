package com.ussdplatform.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Redis-backed session cache.
 * Stores lightweight session state for fast reads during active USSD sessions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionCache {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Value("${ussd.session.timeout:120}")
    private int sessionTimeoutSeconds;

    private static final String KEY_PREFIX = "ussd:session:";

    public void put(String sessionId, String key, String value) {
        String redisKey = KEY_PREFIX + sessionId;
        redis.opsForHash().put(redisKey, key, value);
        redis.expire(redisKey, sessionTimeoutSeconds, TimeUnit.SECONDS);
    }

    public String get(String sessionId, String key) {
        Object val = redis.opsForHash().get(KEY_PREFIX + sessionId, key);
        return val != null ? val.toString() : null;
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getAll(String sessionId) {
        Map<Object, Object> raw = redis.opsForHash().entries(KEY_PREFIX + sessionId);
        Map<String, String> result = new HashMap<>();
        raw.forEach((k, v) -> result.put(k.toString(), v.toString()));
        return result;
    }

    public void putAll(String sessionId, Map<String, String> data) {
        if (data == null || data.isEmpty()) return;
        String redisKey = KEY_PREFIX + sessionId;
        redis.opsForHash().putAll(redisKey, new HashMap<>(data));
        redis.expire(redisKey, sessionTimeoutSeconds, TimeUnit.SECONDS);
    }

    public void delete(String sessionId) {
        redis.delete(KEY_PREFIX + sessionId);
    }

    public boolean exists(String sessionId) {
        return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + sessionId));
    }

    public void refresh(String sessionId) {
        redis.expire(KEY_PREFIX + sessionId, sessionTimeoutSeconds, TimeUnit.SECONDS);
    }
}
