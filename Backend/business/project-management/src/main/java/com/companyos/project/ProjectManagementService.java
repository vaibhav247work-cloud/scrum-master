package com.companyos.project;

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

@Service
public class ProjectManagementService {
    private static final Pattern PROJECT_KEY = Pattern.compile("[A-Z][A-Z0-9_]{1,19}");
    private final ProjectStore store;
    private final ProjectTeamStore teamStore;
    private final FeatureEntitlementService featureEntitlements;
    private final AuthorizationService authorization;

    @Autowired
    public ProjectManagementService(
            ProjectStore store,
            ProjectTeamStore teamStore,
            FeatureEntitlementService featureEntitlements,
            AuthorizationService authorization) {
        this.store = store;
        this.teamStore = teamStore;
        this.featureEntitlements = featureEntitlements;
        this.authorization = authorization;
    }

    public ProjectManagementService(ProjectStore store, FeatureEntitlementService featureEntitlements,
                                    AuthorizationService authorization) {
        this(store, null, featureEntitlements, authorization);
    }

    @Transactional
    public ProjectSummary create(UUID tenantId, UUID actorId, CreateProjectCommand command) {
        featureEntitlements.requireEnabled(tenantId, "PROJECTS");
        if (command != null && command.productId() != null) featureEntitlements.requireEnabled(tenantId, "PRODUCT_MANAGEMENT");
        authorization.requirePermission("project.manage");
        return store.create(tenantId, actorId, normalize(command));
    }

    @Transactional(readOnly = true)
    public List<ProjectSummary> list(UUID tenantId) {
        featureEntitlements.requireEnabled(tenantId, "PROJECTS");
        authorization.requirePermission("project.read");
        return store.findAll(tenantId);
    }

    @Transactional(readOnly = true)
    public List<ProjectTeamSummary> listTeams(UUID tenantId, UUID projectId) {
        requireTeamFeature(tenantId);
        authorization.requirePermission("project.team.read");
        return teamStore.findTeams(tenantId, requiredId(projectId, "projectId"));
    }

    @Transactional
    public ProjectTeamSummary addTeam(UUID tenantId, UUID projectId, UUID teamId, UUID actorId, String relationshipRole) {
        requireTeamFeature(tenantId);
        authorization.requirePermission("project.team.manage");
        return teamStore.addTeam(tenantId, requiredId(projectId, "projectId"), requiredId(teamId, "teamId"),
                requiredId(actorId, "actorId"), normalizeRelationshipRole(relationshipRole));
    }

    @Transactional
    public void removeTeam(UUID tenantId, UUID projectId, UUID teamId, UUID actorId) {
        requireTeamFeature(tenantId);
        authorization.requirePermission("project.team.manage");
        teamStore.removeTeam(tenantId, requiredId(projectId, "projectId"), requiredId(teamId, "teamId"), requiredId(actorId, "actorId"));
    }

    private void requireTeamFeature(UUID tenantId) {
        featureEntitlements.requireEnabled(tenantId, "PROJECTS");
        featureEntitlements.requireEnabled(tenantId, "ORGANIZATION_WORKFORCE");
    }

    private static String normalizeRelationshipRole(String value) {
        String normalized = value == null || value.isBlank() ? "DELIVERY" : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("OWNER", "DELIVERY", "CONTRIBUTOR").contains(normalized)) {
            throw new IllegalArgumentException("relationshipRole is invalid");
        }
        return normalized;
    }

    private static UUID requiredId(UUID value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    private static CreateProjectCommand normalize(CreateProjectCommand command) {
        if (command == null) throw new IllegalArgumentException("request body is required");
        String key = required(command.projectKey(), "projectKey", 20).toUpperCase(Locale.ROOT);
        String name = required(command.name(), "name", 240);
        String description = command.description() == null ? "" : command.description().trim();
        if (description.length() > 4000) throw new IllegalArgumentException("description must be at most 4000 characters");
        if (!PROJECT_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("projectKey must start with an uppercase letter and contain only uppercase letters, numbers, or underscores");
        }
        return new CreateProjectCommand(key, name, description, command.productId());
    }

    private static String required(String value, String field, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        if (normalized.length() > maxLength) throw new IllegalArgumentException(field + " must be at most " + maxLength + " characters");
        return normalized;
    }
}
