package com.companyos.cache.redis;

import com.companyos.idempotency.IdempotencyRecord;
import com.companyos.idempotency.IdempotencyStore;
import com.companyos.idempotency.IdempotencyUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.Optional;

@Repository
public class RedisIdempotencyStore implements IdempotencyStore {
    private static final String KEY_PREFIX = "company-os:idempotency:v1:";
    private static final DefaultRedisScript<Long> COMPLETE_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if not current then return 0 end
            local record = cjson.decode(current)
            if record.requestHash ~= ARGV[1] or record.status ~= 'PROCESSING' then return 0 end
            redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if not current then return 0 end
            local record = cjson.decode(current)
            if record.requestHash ~= ARGV[1] or record.status ~= 'PROCESSING' then return 0 end
            redis.call('DEL', KEYS[1])
            return 1
            """, Long.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisIdempotencyStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<IdempotencyRecord> find(String scope, String key) {
        try {
            String value = redis.opsForValue().get(redisKey(scope, key));
            return value == null ? Optional.empty() : Optional.of(objectMapper.readValue(value, IdempotencyRecord.class));
        } catch (Exception exception) {
            throw new IdempotencyUnavailableException(exception);
        }
    }

    @Override
    public boolean reserve(String scope, String key, String requestHash, Duration ttl) {
        try {
            IdempotencyRecord record = new IdempotencyRecord(scope, key, requestHash, "PROCESSING", null,
                    Instant.now().plus(ttl));
            return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(redisKey(scope, key),
                    objectMapper.writeValueAsString(record), ttl));
        } catch (Exception exception) {
            throw new IdempotencyUnavailableException(exception);
        }
    }

    @Override
    public void complete(String scope, String key, String requestHash, String responseJson, Duration ttl) {
        try {
            String redisKey = redisKey(scope, key);
            IdempotencyRecord completed = new IdempotencyRecord(scope, key, requestHash, "COMPLETED",
                    responseJson, Instant.now().plus(ttl));
            redis.execute(COMPLETE_SCRIPT, Collections.singletonList(redisKey), requestHash,
                    objectMapper.writeValueAsString(completed), Long.toString(ttl.getSeconds()));
        } catch (Exception exception) {
            throw new IdempotencyUnavailableException(exception);
        }
    }

    @Override
    public void release(String scope, String key, String requestHash) {
        try {
            String redisKey = redisKey(scope, key);
            redis.execute(RELEASE_SCRIPT, Collections.singletonList(redisKey), requestHash);
        } catch (Exception exception) {
            throw new IdempotencyUnavailableException(exception);
        }
    }

    private static String redisKey(String scope, String key) {
        return KEY_PREFIX + sha256(scope + ":" + key);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
