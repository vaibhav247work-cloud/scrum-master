package com.companyos.cache.redis;

import com.companyos.feature.entitlement.FeatureEntitlement;
import com.companyos.feature.entitlement.FeatureEntitlementCache;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RedisFeatureEntitlementCache implements FeatureEntitlementCache {
    private static final String KEY_PREFIX = "company-os:feature-entitlements:v1:";
    private static final TypeReference<List<FeatureEntitlement>> LIST_TYPE = new TypeReference<>() { };

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisFeatureEntitlementCache(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${cache.feature-entitlements-ttl:PT5M}") Duration ttl) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    @Override
    public Optional<List<FeatureEntitlement>> get(UUID tenantId) {
        try {
            String value = redis.opsForValue().get(key(tenantId));
            return value == null ? Optional.empty() : Optional.of(objectMapper.readValue(value, LIST_TYPE));
        } catch (Exception exception) {
            throw new IllegalStateException("Feature entitlement cache unavailable", exception);
        }
    }

    @Override
    public void put(UUID tenantId, List<FeatureEntitlement> entitlements) {
        try {
            redis.opsForValue().set(key(tenantId), objectMapper.writeValueAsString(entitlements), ttl);
        } catch (Exception exception) {
            throw new IllegalStateException("Feature entitlement cache unavailable", exception);
        }
    }

    @Override
    public void evict(UUID tenantId) {
        try {
            redis.delete(key(tenantId));
        } catch (Exception exception) {
            throw new IllegalStateException("Feature entitlement cache unavailable", exception);
        }
    }

    private static String key(UUID tenantId) {
        return KEY_PREFIX + sha256(tenantId.toString());
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
