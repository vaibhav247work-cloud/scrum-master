package com.companyos.work;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface WorkItemStore {
    WorkItemSummary create(UUID tenantId, UUID actorId, CreateWorkItemCommand command);

    Optional<WorkItemSummary> findById(UUID tenantId, UUID workItemId);

    WorkItemSummary update(UUID tenantId, UUID actorId, UUID workItemId, UpdateWorkItemCommand command);

    WorkItemSummary reorder(UUID tenantId, UUID actorId, UUID workItemId, int targetPosition);

    List<WorkItemSummary> find(UUID tenantId, WorkItemFilter filter);

    List<WorkItemAssignmentHistory> assignmentHistory(UUID tenantId, UUID workItemId);

    WorkScopeChangeRequest createScopeChangeRequest(UUID tenantId, UUID actorId, CreateWorkScopeChangeCommand command);

    List<WorkScopeChangeRequest> scopeChangeRequests(UUID tenantId, UUID workItemId);

    List<WorkScopeChangeRequest> pendingScopeChangeRequests(UUID tenantId);

    WorkScopeChangeRequest decideScopeChangeRequest(UUID tenantId, UUID requestId, UUID actorId,
                                                    boolean approve, String decisionNote);
}
