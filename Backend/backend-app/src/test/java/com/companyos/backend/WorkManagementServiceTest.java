package com.companyos.backend;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.feature.entitlement.FeatureEntitlementStore;
import com.companyos.security.contract.AuthorizationService;
import com.companyos.work.CreateWorkItemCommand;
import com.companyos.work.UpdateWorkItemCommand;
import com.companyos.work.WorkItemStore;
import com.companyos.work.WorkItemSummary;
import com.companyos.work.WorkManagementService;
import com.companyos.work.CreateWorkScopeChangeCommand;
import com.companyos.work.WorkScopeChangeRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WorkManagementServiceTest {
    private final WorkItemStore store = mock(WorkItemStore.class);
    private final FeatureEntitlementStore entitlements = mock(FeatureEntitlementStore.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final WorkManagementService service = new WorkManagementService(
            store, new FeatureEntitlementService(entitlements), authorization);

    @Test
    void createsWithNormalizedDefaultsAndTrimmedText() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        WorkItemSummary created = summary("TODO", null);
        when(entitlements.isEnabled(tenantId, "WORK_MANAGEMENT")).thenReturn(true);
        when(entitlements.isEnabled(tenantId, "PROJECTS")).thenReturn(true);
        when(store.create(eq(tenantId), eq(actorId), any())).thenReturn(created);

        assertEquals(created, service.create(tenantId, actorId,
                new CreateWorkItemCommand(projectId, null, null,
                        "  Build API  ", "  description  ", " task ", " high ")));

        verify(store).create(eq(tenantId), eq(actorId), eq(new CreateWorkItemCommand(
                projectId, null, null, "Build API", "description", "TASK", "HIGH")));
    }

    @Test
    void rejectsSprintWithoutProjectBeforeWriting() {
        UUID tenantId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "WORK_MANAGEMENT")).thenReturn(true);
        when(entitlements.isEnabled(tenantId, "PROJECTS")).thenReturn(true);
        when(entitlements.isEnabled(tenantId, "SCRUM")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.create(tenantId, UUID.randomUUID(),
                new CreateWorkItemCommand(null, UUID.randomUUID(), null, "Work", null, null, null)));

        verifyNoInteractions(store);
    }

    @Test
    void rejectsInvalidWorkTypeAndPriorityBeforeWriting() {
        UUID tenantId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "WORK_MANAGEMENT")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.create(tenantId, UUID.randomUUID(),
                new CreateWorkItemCommand(null, null, null, "Work", null, "EPIC", "HIGH")));
        assertThrows(IllegalArgumentException.class, () -> service.create(tenantId, UUID.randomUUID(),
                new CreateWorkItemCommand(null, null, null, "Work", null, "TASK", "CRITICAL")));

        verifyNoInteractions(store);
    }

    @Test
    void rejectsParentFromAnotherProjectBeforeWriting() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        WorkItemSummary parent = new WorkItemSummary(parentId, "TASK-PARENT", UUID.randomUUID(), null, null,
                "Parent", "", "STORY", "MEDIUM", "TODO", Instant.now());
        when(entitlements.isEnabled(tenantId, "WORK_MANAGEMENT")).thenReturn(true);
        when(entitlements.isEnabled(tenantId, "PROJECTS")).thenReturn(true);
        when(store.findById(tenantId, parentId)).thenReturn(Optional.of(parent));

        assertThrows(IllegalArgumentException.class, () -> service.create(tenantId, UUID.randomUUID(),
                new CreateWorkItemCommand(projectId, null, null, "Child", null, "TASK", "MEDIUM",
                        null, 60, parentId, "It is accepted")));

        verify(store).findById(tenantId, parentId);
        verify(store, never()).create(eq(tenantId), any(), any());
    }

    @Test
    void acceptsSameProjectParentAndCarriesAcceptanceCriteria() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        WorkItemSummary parent = new WorkItemSummary(parentId, "STORY-PARENT", projectId, null, null,
                "Parent", "", "STORY", "MEDIUM", "TODO", Instant.now());
        WorkItemSummary created = summary("TODO", null);
        when(entitlements.isEnabled(tenantId, "WORK_MANAGEMENT")).thenReturn(true);
        when(entitlements.isEnabled(tenantId, "PROJECTS")).thenReturn(true);
        when(store.findById(tenantId, parentId)).thenReturn(Optional.of(parent));
        when(store.create(eq(tenantId), eq(actorId), any())).thenReturn(created);

        assertEquals(created, service.create(tenantId, actorId,
                new CreateWorkItemCommand(projectId, null, null, "Child", "", "TASK", "MEDIUM",
                        null, 60, parentId, "  Given valid data, then it is accepted.  ")));

        verify(store).create(eq(tenantId), eq(actorId), eq(new CreateWorkItemCommand(projectId, null, null,
                "Child", "", "TASK", "MEDIUM", null, 60, parentId,
                "Given valid data, then it is accepted.")));
    }

    @Test
    void updatePreservesOmittedFieldsAndNormalizesChangedFields() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID workItemId = UUID.randomUUID();
        UUID originalAssignee = UUID.randomUUID();
        WorkItemSummary current = new WorkItemSummary(workItemId, "TASK-1", null, null, originalAssignee,
                "Original", "Old description", "TASK", "MEDIUM", "TODO", Instant.now());
        WorkItemSummary updated = summary("IN_PROGRESS", originalAssignee);
        when(entitlements.isEnabled(tenantId, "WORK_MANAGEMENT")).thenReturn(true);
        when(store.findById(tenantId, workItemId)).thenReturn(Optional.of(current));
        when(store.update(eq(tenantId), eq(actorId), eq(workItemId), any())).thenReturn(updated);

        assertEquals(updated, service.update(tenantId, actorId, workItemId,
                new UpdateWorkItemCommand(null, null, null, null, " urgent ", " in_progress ")));

        verify(store).update(eq(tenantId), eq(actorId), eq(workItemId), eq(new UpdateWorkItemCommand(
                "Original", "Old description", originalAssignee, "TASK", "URGENT", "IN_PROGRESS")));
    }

    @Test
    void updateDoesNotWriteWhenItemIsOutsideTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID workItemId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "WORK_MANAGEMENT")).thenReturn(true);
        when(store.findById(tenantId, workItemId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.update(tenantId, UUID.randomUUID(), workItemId,
                new UpdateWorkItemCommand(null, null, null, null, null, "DONE")));

        verify(store).findById(tenantId, workItemId);
    }

    @Test
    void reordersOnlyAnExistingTenantWorkItem() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID workItemId = UUID.randomUUID();
        WorkItemSummary current = summary("TODO", null);
        WorkItemSummary reordered = summary("TODO", null);
        when(entitlements.isEnabled(tenantId, "WORK_MANAGEMENT")).thenReturn(true);
        when(store.findById(tenantId, workItemId)).thenReturn(Optional.of(current));
        when(store.reorder(tenantId, actorId, workItemId, 2)).thenReturn(reordered);

        assertEquals(reordered, service.reorder(tenantId, actorId, workItemId, 2));
        verify(store).reorder(tenantId, actorId, workItemId, 2);
    }

    @Test
    void rejectsOutOfRangeBacklogPositionBeforeWriting() {
        UUID tenantId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "WORK_MANAGEMENT")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.reorder(tenantId, UUID.randomUUID(), UUID.randomUUID(), 10001));
        verifyNoInteractions(store);
    }

    @Test
    void updateCarriesAssignmentReasonWithoutChangingExistingHistoryInService() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID workItemId = UUID.randomUUID();
        UUID originalAssignee = UUID.randomUUID();
        UUID newAssignee = UUID.randomUUID();
        WorkItemSummary current = new WorkItemSummary(workItemId, "TASK-1", null, null, originalAssignee,
                "Original", "Old description", "TASK", "MEDIUM", "TODO", Instant.now());
        WorkItemSummary updated = summary("TODO", newAssignee);
        when(entitlements.isEnabled(tenantId, "WORK_MANAGEMENT")).thenReturn(true);
        when(store.findById(tenantId, workItemId)).thenReturn(Optional.of(current));
        when(store.update(eq(tenantId), eq(actorId), eq(workItemId), any())).thenReturn(updated);

        assertEquals(updated, service.update(tenantId, actorId, workItemId,
                new UpdateWorkItemCommand(null, null, newAssignee, null, null, null, null, " emergency handoff ")));

        verify(store).update(eq(tenantId), eq(actorId), eq(workItemId), eq(new UpdateWorkItemCommand(
                "Original", "Old description", newAssignee, "TASK", "MEDIUM", "TODO", null, "emergency handoff")));
    }

    @Test
    void requestsEmergencyScopeChangeWithNormalizedPriorityAndReason() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID workItemId = UUID.randomUUID();
        WorkScopeChangeRequest request = new WorkScopeChangeRequest(UUID.randomUUID(), workItemId, null,
                "URGENT", null, actorId, true, "Emergency", "REQUESTED", null, "", Instant.now(), null);
        when(entitlements.isEnabled(tenantId, "WORK_MANAGEMENT")).thenReturn(true);
        when(store.createScopeChangeRequest(eq(tenantId), eq(actorId), any())).thenReturn(request);

        assertEquals(request, service.requestScopeChange(tenantId, actorId,
                new CreateWorkScopeChangeCommand(workItemId, " urgent ", null, true, " Emergency ")));
        verify(store).createScopeChangeRequest(eq(tenantId), eq(actorId), eq(new CreateWorkScopeChangeCommand(
                workItemId, "URGENT", null, true, "Emergency")));
    }

    @Test
    void approvesScopeChangeThroughStoreWithDecisionNote() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        WorkScopeChangeRequest decided = new WorkScopeChangeRequest(requestId, UUID.randomUUID(), null,
                "HIGH", null, UUID.randomUUID(), true, "Emergency", "APPROVED", actorId, "Approved",
                Instant.now(), Instant.now());
        when(entitlements.isEnabled(tenantId, "WORK_MANAGEMENT")).thenReturn(true);
        when(store.decideScopeChangeRequest(tenantId, requestId, actorId, true, "Approved")).thenReturn(decided);

        assertEquals(decided, service.decideScopeChange(tenantId, actorId, requestId, true, " Approved "));
        verify(store).decideScopeChangeRequest(tenantId, requestId, actorId, true, "Approved");
    }

    private static WorkItemSummary summary(String status, UUID assignee) {
        return new WorkItemSummary(UUID.randomUUID(), "TASK-1", null, null, assignee,
                "Work", "Description", "TASK", "MEDIUM", status, Instant.now());
    }

}
