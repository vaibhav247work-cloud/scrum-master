package com.companyos.backend;

import com.companyos.demand.CreateDemandCommand;
import com.companyos.demand.DemandManagementService;
import com.companyos.demand.DemandStore;
import com.companyos.demand.DemandSummary;
import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.feature.entitlement.FeatureEntitlementStore;
import com.companyos.security.contract.AuthorizationService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DemandManagementServiceTest {
    private final DemandStore store = mock(DemandStore.class);
    private final FeatureEntitlementStore entitlements = mock(FeatureEntitlementStore.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final DemandManagementService service = new DemandManagementService(
            store, new FeatureEntitlementService(entitlements), authorization);

    @Test
    void createsNormalizedDemandInsideEnabledTenantFeature() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "DEMAND_INTAKE")).thenReturn(true);
        when(store.create(eq(tenantId), eq(actorId), any())).thenReturn(summary("SUBMITTED"));

        service.create(tenantId, actorId, new CreateDemandCommand("pay-1", "Payment retry", "Need retry handling", "idea", "high"));

        verify(authorization).requirePermission("demand.create");
        verify(store).create(eq(tenantId), eq(actorId), eq(new CreateDemandCommand(
                "PAY-1", "Payment retry", "Need retry handling", "IDEA", "HIGH")));
    }

    @Test
    void decisionUsesExplicitApprovalPermissionAndStatus() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID demandId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "DEMAND_INTAKE")).thenReturn(true);
        when(store.decide(eq(tenantId), eq(demandId), eq(actorId), eq("APPROVED"), any(), eq("Approved for discovery")))
                .thenReturn(summary("APPROVED"));

        assertEquals("APPROVED", service.decide(tenantId, actorId, demandId, "approved", null, "Approved for discovery").status());
        verify(authorization).requirePermission("demand.approve");
    }

    private static DemandSummary summary(String status) {
        Instant now = Instant.now();
        return new DemandSummary(UUID.randomUUID(), "PAY-1", "Payment retry", "", "IDEA", "HIGH", status,
                UUID.randomUUID(), null, null, "", null, now, now);
    }
}
