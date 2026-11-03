package com.companyos.backend;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.feature.entitlement.FeatureEntitlementStore;
import com.companyos.portfolio.CreatePortfolioCommand;
import com.companyos.portfolio.PortfolioManagementService;
import com.companyos.portfolio.PortfolioManagementStore;
import com.companyos.portfolio.PortfolioSummary;
import com.companyos.security.contract.AuthorizationService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PortfolioManagementServiceTest {
    private final PortfolioManagementStore store = mock(PortfolioManagementStore.class);
    private final FeatureEntitlementStore entitlements = mock(FeatureEntitlementStore.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final PortfolioManagementService service = new PortfolioManagementService(
            store, new FeatureEntitlementService(entitlements), authorization);

    @Test
    void normalizesPortfolioKeyAndDefaultsPlanningStatus() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        PortfolioSummary summary = new PortfolioSummary(UUID.randomUUID(), "CUSTOMER_VALUE", "Customer Value", "", "PLANNING", null, Instant.now(), Instant.now());
        when(entitlements.isEnabled(tenantId, "PORTFOLIO_PLANNING")).thenReturn(true);
        when(store.createPortfolio(eq(tenantId), eq(actorId), any(), any())).thenReturn(summary);

        assertEquals(summary, service.createPortfolio(tenantId, actorId,
                new CreatePortfolioCommand(" customer-value ", " Customer Value ", null, null)));

        verify(store).createPortfolio(eq(tenantId), eq(actorId),
                eq(new CreatePortfolioCommand("CUSTOMER_VALUE", "Customer Value", "", "PLANNING")), any());
    }

    @Test
    void rejectsInvalidStatusBeforeWriting() {
        UUID tenantId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "PORTFOLIO_PLANNING")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.createPortfolio(tenantId, UUID.randomUUID(),
                new CreatePortfolioCommand("PORTFOLIO", "Portfolio", "", "UNKNOWN")));
        verifyNoInteractions(store);
    }
}
