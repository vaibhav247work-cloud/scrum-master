package com.companyos.backend;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.feature.entitlement.FeatureEntitlementStore;
import com.companyos.security.contract.AuthorizationService;
import com.companyos.sprint.CreateSprintCommand;
import com.companyos.sprint.SprintCarryOverResult;
import com.companyos.sprint.SprintManagementService;
import com.companyos.sprint.SprintStore;
import com.companyos.sprint.SprintSummary;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SprintManagementServiceTest {
    private final SprintStore store = mock(SprintStore.class);
    private final FeatureEntitlementStore entitlements = mock(FeatureEntitlementStore.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final SprintManagementService service = new SprintManagementService(
            store, new FeatureEntitlementService(entitlements), authorization);

    @Test
    void createNormalizesKeyAndTextBeforeWriting() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        SprintSummary created = new SprintSummary(UUID.randomUUID(), projectId, "SPRINT_1", "Sprint",
                "Goal", null, null, "PLANNED", Instant.now());
        when(entitlements.isEnabled(tenantId, "PROJECTS")).thenReturn(true);
        when(entitlements.isEnabled(tenantId, "SCRUM")).thenReturn(true);
        when(store.create(eq(tenantId), eq(projectId), eq(actorId), any())).thenReturn(created);

        assertEquals(created, service.create(tenantId, projectId, actorId,
                new CreateSprintCommand(" sprint_1 ", " Sprint ", " Goal ", null, null)));

        verify(store).create(eq(tenantId), eq(projectId), eq(actorId), eq(new CreateSprintCommand(
                "SPRINT_1", "Sprint", "Goal", null, null)));
    }

    @Test
    void rejectsInvalidDateRangeBeforeWriting() {
        UUID tenantId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "PROJECTS")).thenReturn(true);
        when(entitlements.isEnabled(tenantId, "SCRUM")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.create(tenantId, UUID.randomUUID(), UUID.randomUUID(),
                new CreateSprintCommand("SPRINT_1", "Sprint", null,
                        LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 19))));

        verifyNoInteractions(store);
    }

    @Test
    void rejectsMalformedSprintKeyBeforeWriting() {
        UUID tenantId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "PROJECTS")).thenReturn(true);
        when(entitlements.isEnabled(tenantId, "SCRUM")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.create(tenantId, UUID.randomUUID(), UUID.randomUUID(),
                new CreateSprintCommand("1-NOT-VALID", "Sprint", null, null, null)));

        verifyNoInteractions(store);
    }

    @Test
    void listRequiresBothEntitlementsAndUsesTenantProjectScope() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "PROJECTS")).thenReturn(true);
        when(entitlements.isEnabled(tenantId, "SCRUM")).thenReturn(true);
        when(store.findByProject(tenantId, projectId)).thenReturn(java.util.List.of());

        assertEquals(java.util.List.of(), service.list(tenantId, projectId));

        verify(store).findByProject(tenantId, projectId);
    }

    @Test
    void completesActiveSprintAndPersistsCompletionNote() {
        UUID tenantId = UUID.randomUUID();
        UUID sprintId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        SprintSummary current = new SprintSummary(sprintId, UUID.randomUUID(), "S1", "Sprint", "Goal",
                null, null, "ACTIVE", Instant.now());
        SprintSummary completed = new SprintSummary(sprintId, current.projectId(), current.sprintKey(), current.name(),
                current.goal(), null, null, "COMPLETED", current.createdAt());
        when(entitlements.isEnabled(tenantId, "PROJECTS")).thenReturn(true);
        when(entitlements.isEnabled(tenantId, "SCRUM")).thenReturn(true);
        when(store.findById(tenantId, sprintId)).thenReturn(java.util.Optional.of(current));
        when(store.updateStatus(tenantId, sprintId, actorId, "COMPLETED", "Shipped")).thenReturn(completed);

        assertEquals(completed, service.changeStatus(tenantId, sprintId, actorId, "completed", " Shipped "));
        verify(store).updateStatus(tenantId, sprintId, actorId, "COMPLETED", "Shipped");
    }

    @Test
    void rejectsCompletingPlannedSprintBeforeWriting() {
        UUID tenantId = UUID.randomUUID();
        UUID sprintId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "PROJECTS")).thenReturn(true);
        when(entitlements.isEnabled(tenantId, "SCRUM")).thenReturn(true);
        when(store.findById(tenantId, sprintId)).thenReturn(java.util.Optional.of(new SprintSummary(
                sprintId, UUID.randomUUID(), "S1", "Sprint", "Goal", null, null, "PLANNED", Instant.now())));

        assertThrows(IllegalArgumentException.class,
                () -> service.changeStatus(tenantId, sprintId, UUID.randomUUID(), "COMPLETED", "too early"));
        verify(store, org.mockito.Mockito.never()).updateStatus(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void carriesOverSelectedWorkOnlyThroughTheSprintStore() {
        UUID tenantId = UUID.randomUUID();
        UUID sourceSprintId = UUID.randomUUID();
        UUID targetSprintId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Set<UUID> workItemIds = Set.of(UUID.randomUUID());
        SprintCarryOverResult result = new SprintCarryOverResult(sourceSprintId, targetSprintId, 1, 2);
        when(entitlements.isEnabled(tenantId, "PROJECTS")).thenReturn(true);
        when(entitlements.isEnabled(tenantId, "SCRUM")).thenReturn(true);
        when(entitlements.isEnabled(tenantId, "WORK_MANAGEMENT")).thenReturn(true);
        when(store.carryOver(tenantId, sourceSprintId, targetSprintId, actorId, workItemIds)).thenReturn(result);

        assertEquals(result, service.carryOver(tenantId, sourceSprintId, targetSprintId, actorId, workItemIds));
        verify(store).carryOver(tenantId, sourceSprintId, targetSprintId, actorId, workItemIds);
    }
}
