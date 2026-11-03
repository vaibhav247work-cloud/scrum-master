package com.companyos.sprint;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.security.contract.AuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.Optional;

@Service
public class SprintManagementService {
    private static final Pattern SPRINT_KEY = Pattern.compile("[A-Z][A-Z0-9_]{1,19}");
    private final SprintStore store;
    private final SprintTeamStore teamStore;
    private final FeatureEntitlementService featureEntitlements;
    private final AuthorizationService authorization;

    @Autowired
    public SprintManagementService(
            SprintStore store,
            SprintTeamStore teamStore,
            FeatureEntitlementService featureEntitlements,
            AuthorizationService authorization) {
        this.store = store;
        this.teamStore = teamStore;
        this.featureEntitlements = featureEntitlements;
        this.authorization = authorization;
    }

    public SprintManagementService(SprintStore store, FeatureEntitlementService featureEntitlements,
                                   AuthorizationService authorization) {
        this(store, null, featureEntitlements, authorization);
    }

    @Transactional
    public SprintSummary create(UUID tenantId, UUID projectId, UUID actorId, CreateSprintCommand command) {
        requireFeatures(tenantId);
        authorization.requirePermission("sprint.manage");
        return store.create(tenantId, projectId, actorId, normalize(command));
    }

    @Transactional(readOnly = true)
    public List<SprintSummary> list(UUID tenantId, UUID projectId) {
        requireFeatures(tenantId);
        authorization.requirePermission("sprint.read");
        return store.findByProject(tenantId, projectId);
    }

    @Transactional(readOnly = true)
    public SprintProgressSummary progress(UUID tenantId, UUID sprintId) {
        requireFeatures(tenantId);
        authorization.requirePermission("sprint.read");
        return store.progress(tenantId, requiredId(sprintId, "sprintId"));
    }

    @Transactional
    public SprintSummary changeStatus(UUID tenantId, UUID sprintId, UUID actorId, String requestedStatus, String completionNote) {
        requireFeatures(tenantId);
        authorization.requirePermission("sprint.manage");
        UUID id = requiredId(sprintId, "sprintId");
        SprintSummary current = store.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("sprint was not found"));
        String next = normalizeStatus(requestedStatus);
        if (!allowedTransition(current.status(), next)) {
            throw new IllegalArgumentException("sprint cannot move from " + current.status() + " to " + next);
        }
        String note = completionNote == null ? "" : completionNote.trim();
        if (note.length() > 4000) throw new IllegalArgumentException("completionNote must be at most 4000 characters");
        return store.updateStatus(tenantId, id, requiredId(actorId, "actorId"), next, note);
    }

    @Transactional
    public SprintCarryOverResult carryOver(UUID tenantId, UUID sourceSprintId, UUID targetSprintId,
                                           UUID actorId, Set<UUID> workItemIds) {
        requireFeatures(tenantId);
        featureEntitlements.requireEnabled(tenantId, "WORK_MANAGEMENT");
        authorization.requirePermission("sprint.manage");
        UUID source = requiredId(sourceSprintId, "sourceSprintId");
        UUID target = requiredId(targetSprintId, "targetSprintId");
        if (source.equals(target)) throw new IllegalArgumentException("source and target sprint must be different");
        Set<UUID> ids = workItemIds == null ? Set.of() : Set.copyOf(workItemIds);
        return store.carryOver(tenantId, source, target, requiredId(actorId, "actorId"), ids);
    }

    @Transactional(readOnly = true)
    public List<SprintTeamSummary> listTeams(UUID tenantId, UUID sprintId) {
        requireTeamFeature(tenantId);
        authorization.requirePermission("sprint.team.read");
        return teamStore.findTeams(tenantId, requiredId(sprintId, "sprintId"));
    }

    @Transactional
    public SprintTeamSummary addTeam(UUID tenantId, UUID sprintId, UUID teamId, UUID actorId, String relationshipRole) {
        requireTeamFeature(tenantId);
        authorization.requirePermission("sprint.team.manage");
        return teamStore.addTeam(tenantId, requiredId(sprintId, "sprintId"), requiredId(teamId, "teamId"),
                requiredId(actorId, "actorId"), normalizeRelationshipRole(relationshipRole));
    }

    @Transactional
    public void removeTeam(UUID tenantId, UUID sprintId, UUID teamId, UUID actorId) {
        requireTeamFeature(tenantId);
        authorization.requirePermission("sprint.team.manage");
        teamStore.removeTeam(tenantId, requiredId(sprintId, "sprintId"), requiredId(teamId, "teamId"), requiredId(actorId, "actorId"));
    }

    private void requireFeatures(UUID tenantId) {
        featureEntitlements.requireEnabled(tenantId, "PROJECTS");
        featureEntitlements.requireEnabled(tenantId, "SCRUM");
    }

    private void requireTeamFeature(UUID tenantId) {
        requireFeatures(tenantId);
        featureEntitlements.requireEnabled(tenantId, "ORGANIZATION_WORKFORCE");
    }

    private static String normalizeRelationshipRole(String value) {
        String normalized = value == null || value.isBlank() ? "DELIVERY" : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("OWNER", "DELIVERY", "CONTRIBUTOR").contains(normalized)) {
            throw new IllegalArgumentException("relationshipRole is invalid");
        }
        return normalized;
    }

    private static String normalizeStatus(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("PLANNED", "ACTIVE", "COMPLETED", "CANCELLED").contains(normalized)) {
            throw new IllegalArgumentException("status is invalid");
        }
        return normalized;
    }

    private static boolean allowedTransition(String current, String next) {
        if (current.equals(next)) return false;
        return switch (current) {
            case "PLANNED" -> next.equals("ACTIVE") || next.equals("CANCELLED");
            case "ACTIVE" -> next.equals("COMPLETED") || next.equals("CANCELLED");
            default -> false;
        };
    }

    private static UUID requiredId(UUID value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    private static CreateSprintCommand normalize(CreateSprintCommand command) {
        if (command == null) throw new IllegalArgumentException("request body is required");
        String key = required(command.sprintKey(), "sprintKey", 20).toUpperCase(Locale.ROOT);
        String name = required(command.name(), "name", 240);
        String goal = command.goal() == null ? "" : command.goal().trim();
        if (goal.length() > 4000) throw new IllegalArgumentException("goal must be at most 4000 characters");
        if (!SPRINT_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("sprintKey must start with an uppercase letter and contain only uppercase letters, numbers, or underscores");
        }
        if (command.startDate() != null && command.endDate() != null && command.endDate().isBefore(command.startDate())) {
            throw new IllegalArgumentException("endDate cannot be before startDate");
        }
        return new CreateSprintCommand(key, name, goal, command.startDate(), command.endDate());
    }

    private static String required(String value, String field, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        if (normalized.length() > maxLength) throw new IllegalArgumentException(field + " must be at most " + maxLength + " characters");
        return normalized;
    }
}
