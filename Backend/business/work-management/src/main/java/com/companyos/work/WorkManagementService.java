package com.companyos.work;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.security.contract.AuthorizationService;
import com.companyos.workflow.WorkflowPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class WorkManagementService {
    private static final Set<String> WORK_TYPES = Set.of("TASK", "BUG", "STORY", "DAILY_TASK", "SIDE_TASK");
    private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "URGENT");
    private static final Pattern STATUS = Pattern.compile("[A-Z][A-Z0-9_]{1,31}");
    private final WorkItemStore store;
    private final FeatureEntitlementService featureEntitlements;
    private final AuthorizationService authorization;
    private final WorkflowPolicy workflowPolicy;

    @Autowired
    public WorkManagementService(
            WorkItemStore store,
            FeatureEntitlementService featureEntitlements,
            AuthorizationService authorization,
            WorkflowPolicy workflowPolicy) {
        this.store = store;
        this.featureEntitlements = featureEntitlements;
        this.authorization = authorization;
        this.workflowPolicy = workflowPolicy;
    }

    public WorkManagementService(WorkItemStore store, FeatureEntitlementService featureEntitlements,
                                 AuthorizationService authorization) {
        this(store, featureEntitlements, authorization, WorkflowPolicy.allowAll());
    }

    @Transactional
    public WorkItemSummary create(UUID tenantId, UUID actorId, CreateWorkItemCommand command) {
        featureEntitlements.requireEnabled(tenantId, "WORK_MANAGEMENT");
        requireRelatedFeatures(tenantId, command);
        authorization.requirePermission("work.manage");
        CreateWorkItemCommand normalized = normalize(command);
        validateParent(tenantId, null, normalized.projectId(), normalized.parentWorkItemId());
        return store.create(tenantId, actorId, normalized);
    }

    @Transactional(readOnly = true)
    public java.util.List<WorkItemSummary> find(UUID tenantId, WorkItemFilter filter) {
        featureEntitlements.requireEnabled(tenantId, "WORK_MANAGEMENT");
        if (filter != null && filter.teamId() != null) {
            featureEntitlements.requireEnabled(tenantId, "ORGANIZATION_WORKFORCE");
        }
        authorization.requirePermission("work.read");
        return store.find(tenantId, filter == null ? new WorkItemFilter(null, null, null) : filter);
    }

    @Transactional(readOnly = true)
    public java.util.List<WorkItemAssignmentHistory> assignmentHistory(UUID tenantId, UUID workItemId) {
        featureEntitlements.requireEnabled(tenantId, "WORK_MANAGEMENT");
        authorization.requirePermission("work.read");
        if (workItemId == null) throw new IllegalArgumentException("workItemId is required");
        if (store.findById(tenantId, workItemId).isEmpty()) throw new IllegalArgumentException("work item was not found");
        return store.assignmentHistory(tenantId, workItemId);
    }

    @Transactional(readOnly = true)
    public java.util.List<WorkScopeChangeRequest> scopeChangeRequests(UUID tenantId, UUID workItemId) {
        featureEntitlements.requireEnabled(tenantId, "WORK_MANAGEMENT");
        authorization.requirePermission("work.read");
        if (workItemId == null) throw new IllegalArgumentException("workItemId is required");
        if (store.findById(tenantId, workItemId).isEmpty()) throw new IllegalArgumentException("work item was not found");
        return store.scopeChangeRequests(tenantId, workItemId);
    }

    @Transactional(readOnly = true)
    public java.util.List<WorkScopeChangeRequest> pendingScopeChangeRequests(UUID tenantId) {
        featureEntitlements.requireEnabled(tenantId, "WORK_MANAGEMENT");
        authorization.requirePermission("work.change.approve");
        return store.pendingScopeChangeRequests(tenantId);
    }

    @Transactional
    public WorkScopeChangeRequest requestScopeChange(UUID tenantId, UUID actorId, CreateWorkScopeChangeCommand command) {
        featureEntitlements.requireEnabled(tenantId, "WORK_MANAGEMENT");
        authorization.requirePermission("work.change.request");
        if (command == null || command.workItemId() == null) throw new IllegalArgumentException("workItemId is required");
        if ((command.requestedPriority() == null || command.requestedPriority().isBlank()) && command.targetSprintId() == null) {
            throw new IllegalArgumentException("requestedPriority or targetSprintId is required");
        }
        String priority = command.requestedPriority() == null || command.requestedPriority().isBlank()
                ? null : normalizeEnum(command.requestedPriority(), "requestedPriority", PRIORITIES, "MEDIUM");
        String reason = required(command.reason(), "reason", 4000);
        return store.createScopeChangeRequest(tenantId, actorId, new CreateWorkScopeChangeCommand(
                command.workItemId(), priority, command.targetSprintId(), command.emergency(), reason));
    }

    @Transactional
    public WorkScopeChangeRequest decideScopeChange(UUID tenantId, UUID actorId, UUID requestId,
                                                     boolean approve, String decisionNote) {
        featureEntitlements.requireEnabled(tenantId, "WORK_MANAGEMENT");
        authorization.requirePermission("work.change.approve");
        if (requestId == null) throw new IllegalArgumentException("requestId is required");
        String note = decisionNote == null ? "" : decisionNote.trim();
        if (note.length() > 1000) throw new IllegalArgumentException("decisionNote must be at most 1000 characters");
        return store.decideScopeChangeRequest(tenantId, requestId, actorId, approve, note);
    }

    @Transactional
    public WorkItemSummary update(UUID tenantId, UUID actorId, UUID workItemId, UpdateWorkItemCommand command) {
        featureEntitlements.requireEnabled(tenantId, "WORK_MANAGEMENT");
        if (command != null && command.teamId() != null) {
            featureEntitlements.requireEnabled(tenantId, "ORGANIZATION_WORKFORCE");
        }
        authorization.requirePermission("work.manage");
        if (workItemId == null) throw new IllegalArgumentException("workItemId is required");
        WorkItemSummary current = store.findById(tenantId, workItemId)
                .orElseThrow(() -> new IllegalArgumentException("work item was not found"));
        UpdateWorkItemCommand normalized = normalizeUpdate(current, command);
        validateParent(tenantId, workItemId, current.projectId(), normalized.parentWorkItemId());
        workflowPolicy.validateTransition(tenantId, current.projectId(), current.status(), normalized.status());
        return store.update(tenantId, actorId, workItemId, normalized);
    }

    @Transactional
    public WorkItemSummary reorder(UUID tenantId, UUID actorId, UUID workItemId, int targetPosition) {
        featureEntitlements.requireEnabled(tenantId, "WORK_MANAGEMENT");
        authorization.requirePermission("work.manage");
        if (workItemId == null) throw new IllegalArgumentException("workItemId is required");
        if (targetPosition < 0 || targetPosition > 10000) {
            throw new IllegalArgumentException("targetPosition must be between 0 and 10000");
        }
        if (store.findById(tenantId, workItemId).isEmpty()) {
            throw new IllegalArgumentException("work item was not found");
        }
        return store.reorder(tenantId, actorId, workItemId, targetPosition);
    }

    private void validateParent(UUID tenantId, UUID childWorkItemId, UUID projectId, UUID parentWorkItemId) {
        if (parentWorkItemId == null) return;
        if (projectId == null) {
            throw new IllegalArgumentException("projectId is required when parentWorkItemId is provided");
        }
        if (Objects.equals(childWorkItemId, parentWorkItemId)) {
            throw new IllegalArgumentException("a work item cannot be its own parent");
        }

        UUID cursor = parentWorkItemId;
        Set<UUID> visited = new HashSet<>();
        for (int depth = 0; cursor != null && depth < 100; depth++) {
            if (!visited.add(cursor)) {
                throw new IllegalArgumentException("work item hierarchy contains a cycle");
            }
            WorkItemSummary parent = store.findById(tenantId, cursor)
                    .orElseThrow(() -> new IllegalArgumentException("parent work item was not found"));
            if (!Objects.equals(projectId, parent.projectId())) {
                throw new IllegalArgumentException("parent work item must belong to the same project");
            }
            if (Objects.equals(childWorkItemId, parent.id())) {
                throw new IllegalArgumentException("work item hierarchy cannot contain a cycle");
            }
            cursor = parent.parentWorkItemId();
        }
        if (cursor != null) {
            throw new IllegalArgumentException("work item hierarchy is too deep");
        }
    }

    private void requireRelatedFeatures(UUID tenantId, CreateWorkItemCommand command) {
        if (command != null && command.projectId() != null) {
            featureEntitlements.requireEnabled(tenantId, "PROJECTS");
        }
        if (command != null && command.sprintId() != null) {
            featureEntitlements.requireEnabled(tenantId, "PROJECTS");
            featureEntitlements.requireEnabled(tenantId, "SCRUM");
        }
        if (command != null && command.teamId() != null) {
            featureEntitlements.requireEnabled(tenantId, "ORGANIZATION_WORKFORCE");
        }
    }

    private static CreateWorkItemCommand normalize(CreateWorkItemCommand command) {
        if (command == null) throw new IllegalArgumentException("request body is required");
        if (command.sprintId() != null && command.projectId() == null) {
            throw new IllegalArgumentException("projectId is required when sprintId is provided");
        }
        String title = required(command.title(), "title", 240);
        String description = command.description() == null ? "" : command.description().trim();
        if (description.length() > 4000) throw new IllegalArgumentException("description must be at most 4000 characters");
        String workType = normalizeEnum(command.workType(), "workType", WORK_TYPES, "TASK");
        String priority = normalizeEnum(command.priority(), "priority", PRIORITIES, "MEDIUM");
        Integer estimateMinutes = normalizeEstimate(command.estimateMinutes());
        return new CreateWorkItemCommand(command.projectId(), command.sprintId(), command.assigneeUserId(), title,
                description, workType, priority, command.teamId(), estimateMinutes, command.parentWorkItemId(),
                bounded(command.acceptanceCriteria(), "acceptanceCriteria", 8000));
    }

    private static UpdateWorkItemCommand normalizeUpdate(WorkItemSummary current, UpdateWorkItemCommand command) {
        if (command == null) throw new IllegalArgumentException("request body is required");
        String title = command.title() == null ? current.title() : required(command.title(), "title", 240);
        String description = command.description() == null ? current.description() : command.description().trim();
        if (description.length() > 4000) throw new IllegalArgumentException("description must be at most 4000 characters");
        String workType = command.workType() == null ? current.workType() : normalizeEnum(command.workType(), "workType", WORK_TYPES, current.workType());
        String priority = command.priority() == null ? current.priority() : normalizeEnum(command.priority(), "priority", PRIORITIES, current.priority());
        String status = command.status() == null ? current.status() : normalizeStatus(command.status(), current.status());
        UUID assigneeUserId = command.assigneeUserId() == null ? current.assigneeUserId() : command.assigneeUserId();
        UUID teamId = command.teamId() == null ? current.teamId() : command.teamId();
        String assignmentReason = command.assignmentReason() == null ? "" : command.assignmentReason().trim();
        if (assignmentReason.length() > 1000) throw new IllegalArgumentException("assignmentReason must be at most 1000 characters");
        Integer estimateMinutes = command.estimateMinutes() == null ? current.estimateMinutes() : normalizeEstimate(command.estimateMinutes());
        UUID parentWorkItemId = command.parentWorkItemId() == null ? current.parentWorkItemId() : command.parentWorkItemId();
        String acceptanceCriteria = command.acceptanceCriteria() == null ? current.acceptanceCriteria() : bounded(command.acceptanceCriteria(), "acceptanceCriteria", 8000);
        return new UpdateWorkItemCommand(title, description, assigneeUserId, workType, priority, status, teamId, assignmentReason,
                estimateMinutes, parentWorkItemId, acceptanceCriteria);
    }

    private static Integer normalizeEstimate(Integer value) {
        if (value == null) return null;
        if (value == 0) return null;
        if (value < 1 || value > 10080) throw new IllegalArgumentException("estimateMinutes must be between 1 and 10080, or 0 to clear");
        return value;
    }

    private static String normalizeEnum(String value, String field, Set<String> allowed, String defaultValue) {
        String normalized = value == null || value.isBlank() ? defaultValue : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new IllegalArgumentException(field + " is invalid");
        return normalized;
    }

    private static String normalizeStatus(String value, String defaultValue) {
        String normalized = value == null || value.isBlank() ? defaultValue : value.trim().toUpperCase(Locale.ROOT);
        if (!STATUS.matcher(normalized).matches()) throw new IllegalArgumentException("status is invalid");
        return normalized;
    }

    private static String required(String value, String field, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        if (normalized.length() > maxLength) throw new IllegalArgumentException(field + " must be at most " + maxLength + " characters");
        return normalized;
    }

    private static String bounded(String value, String field, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maxLength) throw new IllegalArgumentException(field + " must be at most " + maxLength + " characters");
        return normalized;
    }
}
