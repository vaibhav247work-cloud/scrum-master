package com.companyos.backend;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.feature.entitlement.FeatureEntitlementStore;
import com.companyos.reporting.ReleaseReadinessRow;
import com.companyos.reporting.ReleaseReadinessService;
import com.companyos.reporting.ReleaseReadinessStore;
import com.companyos.security.contract.AuthorizationService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReleaseReadinessServiceTest {
    private final ReleaseReadinessStore readiness = mock(ReleaseReadinessStore.class);
    private final FeatureEntitlementStore entitlements = mock(FeatureEntitlementStore.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final ReleaseReadinessService service = new ReleaseReadinessService(
            readiness, new FeatureEntitlementService(entitlements), authorization);

    @Test
    void requiresReportingAndReleaseFeaturesBeforeReading() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        List<ReleaseReadinessRow> expected = List.of(new ReleaseReadinessRow(
                UUID.randomUUID(), projectId, "WEB", "2026.10", "Checkout", null,
                "IN_PROGRESS", 10, 8, 2, 0, 80, null, "NO_TARGET"));
        when(entitlements.isEnabled(tenantId, "REPORTING")).thenReturn(true);
        when(entitlements.isEnabled(tenantId, "RELEASE_MANAGEMENT")).thenReturn(true);
        when(readiness.readiness(tenantId, projectId)).thenReturn(expected);

        assertEquals(expected, service.readiness(tenantId, projectId));

        verify(authorization).requirePermission("report.read");
        verify(readiness).readiness(tenantId, projectId);
    }

    @Test
    void blocksDisabledReleaseManagementBeforeStoreAccess() {
        UUID tenantId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "REPORTING")).thenReturn(true);
        when(entitlements.isEnabled(tenantId, "RELEASE_MANAGEMENT")).thenReturn(false);

        assertThrows(FeatureEntitlementService.FeatureNotEnabledException.class,
                () -> service.readiness(tenantId, null));

        verifyNoInteractions(authorization, readiness);
    }
}
