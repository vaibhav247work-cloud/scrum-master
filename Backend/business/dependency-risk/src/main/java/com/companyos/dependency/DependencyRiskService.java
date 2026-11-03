package com.companyos.dependency;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.security.contract.AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class DependencyRiskService {
    private static final Set<String> TYPES = Set.of("BLOCKS", "RELATED", "DUPLICATES");
    private final DependencyStore store;
    private final FeatureEntitlementService featureEntitlements;
    private final AuthorizationService authorization;

    public DependencyRiskService(DependencyStore store, FeatureEntitlementService featureEntitlements,
                                  AuthorizationService authorization) {
        this.store = store;
        this.featureEntitlements = featureEntitlements;
        this.authorization = authorization;
    }

    @Transactional
    public DependencySummary create(UUID tenantId, UUID actorId, UUID blockedWorkItemId, CreateDependencyCommand command) {
        requireEnabled(tenantId);
        authorization.requirePermission("dependency.manage");
        if (command == null || command.dependsOnWorkItemId() == null) throw new IllegalArgumentException("dependsOnWorkItemId is required");
        if (blockedWorkItemId.equals(command.dependsOnWorkItemId())) throw new IllegalArgumentException("A work item cannot depend on itself");
        String type = command.dependencyType() == null || command.dependencyType().isBlank()
                ? "BLOCKS" : command.dependencyType().trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(type)) throw new IllegalArgumentException("dependencyType is invalid");
        return store.create(tenantId, actorId, blockedWorkItemId, new CreateDependencyCommand(command.dependsOnWorkItemId(), type));
    }

    @Transactional(readOnly = true)
    public List<DependencySummary> list(UUID tenantId, UUID workItemId) {
        requireEnabled(tenantId);
        authorization.requirePermission("dependency.read");
        return store.findForWorkItem(tenantId, workItemId);
    }

    private void requireEnabled(UUID tenantId) {
        featureEntitlements.requireEnabled(tenantId, "DEPENDENCY_RISK");
        featureEntitlements.requireEnabled(tenantId, "WORK_MANAGEMENT");
    }
}
