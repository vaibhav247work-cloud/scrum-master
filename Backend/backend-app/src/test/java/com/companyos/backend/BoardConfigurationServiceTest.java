package com.companyos.backend;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.security.contract.AuthorizationService;
import com.companyos.workflow.BoardConfigurationCommand;
import com.companyos.workflow.BoardConfigurationService;
import com.companyos.workflow.BoardConfigurationStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class BoardConfigurationServiceTest {
    @Test
    void invalidBoardGroupingIsRejectedBeforeStorage() {
        BoardConfigurationStore store = mock(BoardConfigurationStore.class);
        FeatureEntitlementService entitlements = mock(FeatureEntitlementService.class);
        AuthorizationService authorization = mock(AuthorizationService.class);
        BoardConfigurationService service = new BoardConfigurationService(store, entitlements, authorization);

        assertThrows(IllegalArgumentException.class, () -> service.update(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BoardConfigurationCommand("FREEFORM", "NONE", List.of("TITLE"), Map.of())));

        verifyNoInteractions(store);
    }

    @Test
    void boardConfigurationRequiresPlanningFeatureAndPermission() {
        BoardConfigurationStore store = mock(BoardConfigurationStore.class);
        FeatureEntitlementService entitlements = mock(FeatureEntitlementService.class);
        AuthorizationService authorization = mock(AuthorizationService.class);
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        BoardConfigurationService service = new BoardConfigurationService(store, entitlements, authorization);

        service.update(tenantId, actorId, projectId,
                new BoardConfigurationCommand("STATUS", "NONE", List.of("KEY"), Map.of("IN_PROGRESS", 4)));

        verify(entitlements).requireEnabled(tenantId, "PLANNING_WORKFLOWS");
        verify(authorization).requirePermission("workflow.manage");
    }
}
