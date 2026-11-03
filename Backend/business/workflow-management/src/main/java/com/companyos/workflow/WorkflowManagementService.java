package com.companyos.workflow;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.security.contract.AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class WorkflowManagementService implements WorkflowPolicy {
    private static final Pattern KEY = Pattern.compile("[A-Z][A-Z0-9_]{1,31}");
    private static final Set<String> METHODOLOGIES = Set.of("SCRUM", "KANBAN", "HYBRID", "CUSTOM");
    private final WorkflowStore store;
    private final FeatureEntitlementService featureEntitlements;
    private final AuthorizationService authorization;

    public WorkflowManagementService(WorkflowStore store, FeatureEntitlementService featureEntitlements,
                                     AuthorizationService authorization) {
        this.store = store;
        this.featureEntitlements = featureEntitlements;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public List<WorkflowSummary> list(UUID tenantId) {
        requireFeature(tenantId);
        authorization.requirePermission("workflow.read");
        return store.findAll(tenantId);
    }

    @Transactional
    public WorkflowSummary create(UUID tenantId, UUID actorId, CreateWorkflowCommand command) {
        requireFeature(tenantId);
        authorization.requirePermission("workflow.manage");
        return store.create(tenantId, actorId, normalize(command));
    }

    @Transactional
    public void assignToProject(UUID tenantId, UUID projectId, UUID workflowId) {
        requireFeature(tenantId);
        authorization.requirePermission("workflow.manage");
        if (projectId == null || workflowId == null) throw new IllegalArgumentException("projectId and workflowId are required");
        store.assignToProject(tenantId, projectId, workflowId);
    }

    @Override
    public void validateTransition(UUID tenantId, UUID projectId, String fromStatus, String toStatus) {
        if (projectId == null || fromStatus == null || toStatus == null || fromStatus.equals(toStatus)) return;
        if (!featureEntitlements.isEnabled(tenantId, "PLANNING_WORKFLOWS")) return;
        if (!store.transitionAllowed(tenantId, projectId, fromStatus, toStatus)) {
            throw new IllegalArgumentException("status transition is not allowed by the project workflow");
        }
    }

    private void requireFeature(UUID tenantId) { featureEntitlements.requireEnabled(tenantId, "PLANNING_WORKFLOWS"); }

    private static CreateWorkflowCommand normalize(CreateWorkflowCommand command) {
        if (command == null) throw new IllegalArgumentException("request body is required");
        String key = required(command.workflowKey(), "workflowKey", 32).toUpperCase(Locale.ROOT).replace('-', '_');
        if (!KEY.matcher(key).matches()) throw new IllegalArgumentException("workflowKey is invalid");
        String name = required(command.name(), "name", 160);
        String methodology = enumValue(command.methodology(), "methodology", METHODOLOGIES, "CUSTOM");
        if (command.statuses() == null || command.statuses().size() < 2 || command.statuses().size() > 24) {
            throw new IllegalArgumentException("statuses must contain between 2 and 24 entries");
        }
        List<WorkflowStatusDefinition> statuses = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (WorkflowStatusDefinition status : command.statuses()) {
            if (status == null) throw new IllegalArgumentException("status is required");
            String statusKey = required(status.key(), "status.key", 32).toUpperCase(Locale.ROOT);
            if (!KEY.matcher(statusKey).matches() || !keys.add(statusKey)) throw new IllegalArgumentException("status keys must be unique and valid");
            String label = required(status.label(), "status.label", 120);
            if (status.position() < 0 || status.position() > 999) throw new IllegalArgumentException("status.position is invalid");
            statuses.add(new WorkflowStatusDefinition(statusKey, label, status.terminal(), status.position()));
        }
        List<WorkflowTransitionDefinition> transitions = new ArrayList<>();
        if (command.transitions() != null) {
            for (WorkflowTransitionDefinition transition : command.transitions()) {
                if (transition == null) throw new IllegalArgumentException("transition is required");
                String from = required(transition.fromStatus(), "transition.fromStatus", 32).toUpperCase(Locale.ROOT);
                String to = required(transition.toStatus(), "transition.toStatus", 32).toUpperCase(Locale.ROOT);
                if (!keys.contains(from) || !keys.contains(to) || from.equals(to)) throw new IllegalArgumentException("transition references an invalid status");
                transitions.add(new WorkflowTransitionDefinition(from, to));
            }
        }
        return new CreateWorkflowCommand(key, name, methodology, List.copyOf(statuses), List.copyOf(transitions));
    }

    private static String enumValue(String value, String field, Set<String> allowed, String fallback) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new IllegalArgumentException(field + " is invalid");
        return normalized;
    }

    private static String required(String value, String field, int max) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        if (normalized.length() > max) throw new IllegalArgumentException(field + " must be at most " + max + " characters");
        return normalized;
    }
}
