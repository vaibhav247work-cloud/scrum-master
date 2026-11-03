package com.companyos.cache.redis;

import com.companyos.security.contract.SessionData;
import com.companyos.security.contract.SessionStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RedisSessionStore implements SessionStore {
    private static final String KEY_PREFIX = "company-os:session:v1:";
    private static final String USER_INDEX_PREFIX = "company-os:session-user:v1:";
    private static final String TENANT_INDEX_PREFIX = "company-os:session-tenant:v1:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisSessionStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(String rawToken, SessionData session, Duration ttl) {
        try {
            String sessionKey = key(rawToken, session.tenantId());
            redis.opsForValue().set(sessionKey, objectMapper.writeValueAsString(session), ttl);
            redis.opsForSet().add(userIndex(session.tenantId(), session.userId()), sessionKey);
            redis.expire(userIndex(session.tenantId(), session.userId()), ttl);
            redis.opsForSet().add(tenantIndex(session.tenantId()), sessionKey);
            redis.expire(tenantIndex(session.tenantId()), ttl);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize session", exception);
        }
    }

    @Override
    public Optional<SessionData> find(String rawToken) {
        UUID tenantId = tenantId(rawToken);
        if (tenantId == null) {
            return Optional.empty();
        }
        try {
            String value = redis.opsForValue().get(key(rawToken, tenantId));
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(value, SessionData.class));
        } catch (Exception exception) {
            // Authentication must fail closed if the session cache is unavailable or corrupt.
            return Optional.empty();
        }
    }

    @Override
    public void revoke(String rawToken) {
        UUID tenantId = tenantId(rawToken);
        if (tenantId != null) {
            String sessionKey = key(rawToken, tenantId);
            String value = redis.opsForValue().get(sessionKey);
            redis.delete(sessionKey);
            if (value != null) {
                try {
                    SessionData session = objectMapper.readValue(value, SessionData.class);
                    redis.opsForSet().remove(userIndex(session.tenantId(), session.userId()), sessionKey);
                    redis.opsForSet().remove(tenantIndex(session.tenantId()), sessionKey);
                } catch (Exception ignored) {
                    // The session is already revoked; stale index cleanup is best effort.
                }
            }
        }
    }

    @Override
    public void revokeAll(UUID tenantId, UUID userId) {
        String index = userIndex(tenantId, userId);
        var keys = redis.opsForSet().members(index);
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
        redis.delete(index);
    }

    @Override
    public void revokeAllForTenant(UUID tenantId) {
        String index = tenantIndex(tenantId);
        var keys = redis.opsForSet().members(index);
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
        redis.delete(index);
    }

    private static String key(String rawToken, UUID tenantId) {
        return KEY_PREFIX + tenantId + ":" + sha256(rawToken);
    }

    private static String userIndex(UUID tenantId, UUID userId) {
        return USER_INDEX_PREFIX + tenantId + ":" + userId;
    }

    private static String tenantIndex(UUID tenantId) {
        return TENANT_INDEX_PREFIX + tenantId;
    }

    private static UUID tenantId(String rawToken) {
        if (rawToken == null) {
            return null;
        }
        int separator = rawToken.indexOf('.');
        if (separator <= 0) {
            return null;
        }
        try {
            return UUID.fromString(rawToken.substring(0, separator));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
