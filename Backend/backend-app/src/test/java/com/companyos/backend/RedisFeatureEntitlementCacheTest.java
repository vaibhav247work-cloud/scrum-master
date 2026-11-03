package com.companyos.backend;

import com.companyos.cache.redis.RedisFeatureEntitlementCache;
import com.companyos.feature.entitlement.FeatureEntitlement;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisFeatureEntitlementCacheTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final Duration ttl = Duration.ofMinutes(5);
    private final RedisFeatureEntitlementCache cache = new RedisFeatureEntitlementCache(
            redis, new com.fasterxml.jackson.databind.ObjectMapper(), ttl);

    RedisFeatureEntitlementCacheTest() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void usesDifferentOpaqueTenantKeysAndDoesNotExposeTenantId() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        when(values.get(anyString())).thenReturn(null);

        cache.get(tenantA);
        cache.get(tenantB);

        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(values, org.mockito.Mockito.times(2)).get(captor.capture());
        String keyA = captor.getAllValues().get(0);
        String keyB = captor.getAllValues().get(1);
        assertNotEquals(keyA, keyB);
        assertTrue(keyA.startsWith("company-os:feature-entitlements:v1:"));
        assertTrue(!keyA.contains(tenantA.toString()));
        assertTrue(!keyB.contains(tenantB.toString()));
    }

    @Test
    void writesWithBoundedTtlAndEvictsTheTenantKey() {
        UUID tenantId = UUID.randomUUID();
        List<FeatureEntitlement> entitlements = List.of(
                new FeatureEntitlement("PROJECTS", "Projects", true, "ACTIVE"));

        cache.put(tenantId, entitlements);
        cache.evict(tenantId);

        verify(values).set(anyString(), anyString(), eq(ttl));
        verify(redis).delete(anyString());
    }
}
