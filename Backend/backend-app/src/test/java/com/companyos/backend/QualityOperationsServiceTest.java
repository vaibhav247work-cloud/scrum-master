package com.companyos.backend;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.feature.entitlement.FeatureEntitlementStore;
import com.companyos.quality.CreateQualityGateCommand;
import com.companyos.quality.QualityGateSummary;
import com.companyos.quality.QualityOperationsService;
import com.companyos.quality.QualityOperationsStore;
import com.companyos.security.contract.AuthorizationService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QualityOperationsServiceTest {
    private final QualityOperationsStore store = mock(QualityOperationsStore.class);
    private final FeatureEntitlementStore entitlements = mock(FeatureEntitlementStore.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final QualityOperationsService service = new QualityOperationsService(store, new FeatureEntitlementService(entitlements), authorization);

    @Test
    void createsNormalizedQualityGate() {
        UUID tenantId = UUID.randomUUID(); UUID actorId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "QUALITY_GATES")).thenReturn(true);
        when(store.createGate(eq(tenantId), eq(actorId), any())).thenReturn(summary());

        service.createGate(tenantId, actorId, new CreateQualityGateCommand(projectId, null, "security-check", "security", true));

        verify(authorization).requirePermission("quality.manage");
        verify(store).createGate(eq(tenantId), eq(actorId), eq(new CreateQualityGateCommand(
                projectId, null, "SECURITY_CHECK", "SECURITY", true)));
    }

    @Test
    void requiresSeparatePermissionToWaiveGate() {
        UUID tenantId = UUID.randomUUID(); UUID actorId = UUID.randomUUID(); UUID gateId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "QUALITY_GATES")).thenReturn(true);
        when(store.decideGate(eq(tenantId), eq(actorId), eq(gateId), eq("WAIVED"), eq("accepted risk"))).thenReturn(summary());

        service.decideGate(tenantId, actorId, gateId, "waived", "accepted risk");

        verify(authorization).requirePermission("quality.waive");
    }

    @Test
    void rejectsGateWithoutProjectOrReleaseScope() {
        UUID tenantId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "QUALITY_GATES")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.createGate(tenantId, UUID.randomUUID(),
                new CreateQualityGateCommand(null, null, "SECURITY", "SECURITY", true)));
    }

    private static QualityGateSummary summary() { return new QualityGateSummary(UUID.randomUUID(), null, null, "SECURITY_CHECK", "SECURITY", "OPEN", true, "", null, null, Instant.now(), Instant.now()); }
}
