package com.companyos.backend;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.feature.entitlement.FeatureEntitlementStore;
import com.companyos.organization.CreateOrganizationUnitCommand;
import com.companyos.organization.CreateTeamCommand;
import com.companyos.organization.OrganizationManagementService;
import com.companyos.organization.OrganizationStore;
import com.companyos.organization.TeamSummary;
import com.companyos.security.contract.AuthorizationService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrganizationManagementServiceTest {
    private final OrganizationStore store = mock(OrganizationStore.class);
    private final FeatureEntitlementStore entitlementStore = mock(FeatureEntitlementStore.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final OrganizationManagementService service = new OrganizationManagementService(
            store, new FeatureEntitlementService(entitlementStore), authorization);

    @Test
    void createsTenantScopedTeamOnlyAfterFeatureAndPermissionChecks() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        TeamSummary expected = new TeamSummary(UUID.randomUUID(), null, "PLATFORM", "Platform", actorId,
                "ACTIVE", 0, Instant.now());
        when(entitlementStore.isEnabled(tenantId, "ORGANIZATION_WORKFORCE")).thenReturn(true);
        when(store.createTeam(eq(tenantId), eq(actorId), any(), any())).thenReturn(expected);

        TeamSummary result = service.createTeam(tenantId, actorId,
                new CreateTeamCommand(null, " platform ", "Platform", actorId));

        assertEquals(expected, result);
        verify(authorization).requirePermission("team.manage");
        verify(store).createTeam(eq(tenantId), eq(actorId), any(), any());
    }

    @Test
    void normalizesOrganizationKeysAndRejectsUnsupportedUnitTypes() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(entitlementStore.isEnabled(tenantId, "ORGANIZATION_WORKFORCE")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.createUnit(tenantId, actorId,
                new CreateOrganizationUnitCommand(null, "eng", "Engineering", "TEAM", actorId)));

        verifyNoInteractions(store);
    }

    @Test
    void rejectsInvalidMembershipRoleBeforeWriting() {
        UUID tenantId = UUID.randomUUID();
        when(entitlementStore.isEnabled(tenantId, "ORGANIZATION_WORKFORCE")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.addMember(tenantId, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "OWNER"));

        verifyNoInteractions(store);
    }

    @Test
    void disabledOrganizationFeatureFailsBeforeReadingTenantData() {
        UUID tenantId = UUID.randomUUID();
        when(entitlementStore.isEnabled(tenantId, "ORGANIZATION_WORKFORCE")).thenReturn(false);

        assertThrows(FeatureEntitlementService.FeatureNotEnabledException.class,
                () -> service.teams(tenantId));

        verifyNoInteractions(authorization, store);
    }
}
