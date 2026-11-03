package com.companyos.backend;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.feature.entitlement.FeatureEntitlementStore;
import com.companyos.retention.RetentionManagementService;
import com.companyos.retention.RetentionManagementStore;
import com.companyos.retention.RetentionPolicyCommand;
import com.companyos.retention.RetentionPolicySummary;
import com.companyos.security.contract.AuthorizationService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RetentionManagementServiceTest {
    @Test
    void rejectsRetentionBelowProtectedPolicyFloorBeforeStorage() {
        RetentionManagementStore store = mock(RetentionManagementStore.class);
        FeatureEntitlementStore features = mock(FeatureEntitlementStore.class);
        AuthorizationService authorization = mock(AuthorizationService.class);
        UUID tenantId = UUID.randomUUID();
        when(features.isEnabled(tenantId, "RETENTION_MANAGEMENT")).thenReturn(true);

        RetentionManagementService service = new RetentionManagementService(store, new FeatureEntitlementService(features), authorization);
        assertThrows(IllegalArgumentException.class, () -> service.savePolicy(tenantId, UUID.randomUUID(),
                new RetentionPolicyCommand(true, "PURGE", 30, 90, 90, 365)));
        verifyNoInteractions(store);
    }

    @Test
    void cleanupRequiresEnabledPurgePolicy() {
        RetentionManagementStore store = mock(RetentionManagementStore.class);
        FeatureEntitlementStore features = mock(FeatureEntitlementStore.class);
        AuthorizationService authorization = mock(AuthorizationService.class);
        UUID tenantId = UUID.randomUUID();
        when(features.isEnabled(tenantId, "RETENTION_MANAGEMENT")).thenReturn(true);
        when(store.findPolicy(tenantId)).thenReturn(new RetentionPolicySummary(tenantId, false, "PREVIEW_ONLY", 120, 90, 90, 365, 1, Instant.now()));

        RetentionManagementService service = new RetentionManagementService(store, new FeatureEntitlementService(features), authorization);
        assertThrows(IllegalArgumentException.class, () -> service.cleanup(tenantId, UUID.randomUUID()));
        verify(store).findPolicy(tenantId);
    }

    @Test
    void validPolicyRequiresRetentionPermission() {
        RetentionManagementStore store = mock(RetentionManagementStore.class);
        FeatureEntitlementStore features = mock(FeatureEntitlementStore.class);
        AuthorizationService authorization = mock(AuthorizationService.class);
        UUID tenantId = UUID.randomUUID();
        when(features.isEnabled(tenantId, "RETENTION_MANAGEMENT")).thenReturn(true);
        when(store.savePolicy(eq(tenantId), any(), any())).thenReturn(new RetentionPolicySummary(tenantId, true, "PURGE", 120, 90, 90, 365, 1, Instant.now()));

        new RetentionManagementService(store, new FeatureEntitlementService(features), authorization)
                .savePolicy(tenantId, UUID.randomUUID(), new RetentionPolicyCommand(true, "PURGE", 120, 90, 90, 365));

        verify(authorization).requirePermission("retention.manage");
    }
}
