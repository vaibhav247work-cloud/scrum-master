package com.companyos.release;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.security.contract.AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ReleaseManagementService {
    private final ReleaseStore store;
    private final FeatureEntitlementService featureEntitlements;
    private final AuthorizationService authorization;

    public ReleaseManagementService(ReleaseStore store, FeatureEntitlementService featureEntitlements,
                                     AuthorizationService authorization) {
        this.store = store;
        this.featureEntitlements = featureEntitlements;
        this.authorization = authorization;
    }

    @Transactional
    public ReleaseSummary create(UUID tenantId, UUID projectId, UUID actorId, CreateReleaseCommand command) {
        featureEntitlements.requireEnabled(tenantId, "RELEASE_MANAGEMENT");
        featureEntitlements.requireEnabled(tenantId, "PROJECTS");
        authorization.requirePermission("release.manage");
        return store.create(tenantId, projectId, actorId, normalize(command));
    }

    @Transactional(readOnly = true)
    public List<ReleaseSummary> list(UUID tenantId, UUID projectId) {
        featureEntitlements.requireEnabled(tenantId, "RELEASE_MANAGEMENT");
        featureEntitlements.requireEnabled(tenantId, "PROJECTS");
        authorization.requirePermission("release.read");
        return store.findByProject(tenantId, projectId);
    }

    @Transactional
    public void addWorkItem(UUID tenantId, UUID actorId, UUID releaseId, UUID workItemId) {
        featureEntitlements.requireEnabled(tenantId, "RELEASE_MANAGEMENT");
        featureEntitlements.requireEnabled(tenantId, "WORK_MANAGEMENT");
        authorization.requirePermission("release.manage");
        if (releaseId == null || workItemId == null) throw new IllegalArgumentException("releaseId and workItemId are required");
        store.addWorkItem(tenantId, actorId, releaseId, workItemId);
    }

    private static CreateReleaseCommand normalize(CreateReleaseCommand command) {
        if (command == null) throw new IllegalArgumentException("request body is required");
        String key = required(command.releaseKey(), "releaseKey", 40).toUpperCase(Locale.ROOT);
        String name = required(command.name(), "name", 240);
        String description = command.description() == null ? "" : command.description().trim();
        if (description.length() > 4000) throw new IllegalArgumentException("description must be at most 4000 characters");
        return new CreateReleaseCommand(key, name, description, command.targetDate());
    }

    private static String required(String value, String field, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        if (normalized.length() > maxLength) throw new IllegalArgumentException(field + " must be at most " + maxLength + " characters");
        return normalized;
    }
}
