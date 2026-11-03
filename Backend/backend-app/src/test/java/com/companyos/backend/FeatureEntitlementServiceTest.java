package com.companyos.backend;

import com.companyos.feature.entitlement.FeatureEntitlement;
import com.companyos.feature.entitlement.FeatureEntitlementCache;
import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.feature.entitlement.FeatureEntitlementStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FeatureEntitlementServiceTest {
    private final FeatureEntitlementStore store = mock(FeatureEntitlementStore.class);
    private final FeatureEntitlementCache cache = mock(FeatureEntitlementCache.class);
    private final FeatureEntitlementService service = new FeatureEntitlementService(store, cache);

    @Test
    void readsFromCacheBeforeMySql() {
        UUID tenantId = UUID.randomUUID();
        List<FeatureEntitlement> cached = List.of(new FeatureEntitlement("PROJECTS", "Projects", true, "ACTIVE"));
        when(cache.get(tenantId)).thenReturn(Optional.of(cached));

        assertEquals(cached, service.list(tenantId));

        verify(cache).get(tenantId);
    }

    @Test
    void fallsBackToMySqlAndWarmsCacheWhenCacheIsEmpty() {
        UUID tenantId = UUID.randomUUID();
        List<FeatureEntitlement> entitlements = List.of(new FeatureEntitlement("SCRUM", "Scrum", false, "ACTIVE"));
        when(cache.get(tenantId)).thenReturn(Optional.empty());
        when(store.findByTenantId(tenantId)).thenReturn(entitlements);

        assertEquals(entitlements, service.list(tenantId));

        verify(store).findByTenantId(tenantId);
        verify(cache).put(tenantId, entitlements);
    }

    @Test
    void cacheFailureDoesNotBreakConfigurationReadAndWriteEvicts() {
        UUID tenantId = UUID.randomUUID();
        when(cache.get(tenantId)).thenThrow(new IllegalStateException("redis down"));
        List<FeatureEntitlement> entitlements = List.of();
        when(store.findByTenantId(tenantId)).thenReturn(entitlements);

        assertEquals(entitlements, service.list(tenantId));

        service.setEnabled(tenantId, "projects", true);
        verify(store).setEnabled(eq(tenantId), eq("PROJECTS"), eq(true), any(Instant.class));
        verify(cache).evict(tenantId);
    }

    @Test
    void authorizationAlwaysUsesMySqlAndNeverUsesTheListCache() {
        UUID tenantId = UUID.randomUUID();
        when(store.isEnabled(tenantId, "SCRUM")).thenReturn(false);

        org.junit.jupiter.api.Assertions.assertThrows(
                FeatureEntitlementService.FeatureNotEnabledException.class,
                () -> service.requireEnabled(tenantId, "scrum"));

        verify(store).isEnabled(tenantId, "SCRUM");
        verifyNoInteractions(cache);
    }
}
