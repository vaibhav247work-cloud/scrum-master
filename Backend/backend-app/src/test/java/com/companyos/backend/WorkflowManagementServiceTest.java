package com.companyos.backend;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.feature.entitlement.FeatureEntitlementStore;
import com.companyos.security.contract.AuthorizationService;
import com.companyos.workflow.CreateWorkflowCommand;
import com.companyos.workflow.WorkflowManagementService;
import com.companyos.workflow.WorkflowStatusDefinition;
import com.companyos.workflow.WorkflowStore;
import com.companyos.workflow.WorkflowSummary;
import com.companyos.workflow.WorkflowTransitionDefinition;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowManagementServiceTest {
    private final WorkflowStore store = mock(WorkflowStore.class);
    private final FeatureEntitlementStore entitlements = mock(FeatureEntitlementStore.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final WorkflowManagementService service = new WorkflowManagementService(
            store, new FeatureEntitlementService(entitlements), authorization);

    @Test
    void createsNormalizedWorkflowAndPreservesTransitionGraph() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "PLANNING_WORKFLOWS")).thenReturn(true);
        when(store.create(eq(tenantId), eq(actorId), any())).thenReturn(summary());

        service.create(tenantId, actorId, new CreateWorkflowCommand("support-flow", "Support flow", "kanban",
                List.of(new WorkflowStatusDefinition("todo", "To do", false, 10),
                        new WorkflowStatusDefinition("done", "Done", true, 20)),
                List.of(new WorkflowTransitionDefinition("todo", "done"))));

        verify(authorization).requirePermission("workflow.manage");
        verify(store).create(eq(tenantId), eq(actorId), eq(new CreateWorkflowCommand("SUPPORT_FLOW", "Support flow", "KANBAN",
                List.of(new WorkflowStatusDefinition("TODO", "To do", false, 10),
                        new WorkflowStatusDefinition("DONE", "Done", true, 20)),
                List.of(new WorkflowTransitionDefinition("TODO", "DONE")))));
    }

    @Test
    void rejectsStatusTransitionNotInProjectWorkflow() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "PLANNING_WORKFLOWS")).thenReturn(true);
        when(store.transitionAllowed(tenantId, projectId, "TODO", "DONE")).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> service.validateTransition(tenantId, projectId, "TODO", "DONE"));
    }

    private static WorkflowSummary summary() {
        return new WorkflowSummary(UUID.randomUUID(), "SUPPORT_FLOW", "Support flow", "KANBAN", "ACTIVE", List.of(), List.of(), Instant.now());
    }
}
