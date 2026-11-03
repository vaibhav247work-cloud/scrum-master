package com.companyos.workflow;

import java.util.List;
import java.util.UUID;

public interface WorkflowStore {
    List<WorkflowSummary> findAll(UUID tenantId);

    WorkflowSummary create(UUID tenantId, UUID actorId, CreateWorkflowCommand command);

    void assignToProject(UUID tenantId, UUID projectId, UUID workflowId);

    boolean transitionAllowed(UUID tenantId, UUID projectId, String fromStatus, String toStatus);
}
