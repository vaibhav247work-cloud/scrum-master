package com.companyos.retention;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.security.contract.AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class RetentionManagementService {
    private static final Set<String> MODES = Set.of("PURGE", "PREVIEW_ONLY");
    private final RetentionManagementStore store;
    private final FeatureEntitlementService features;
    private final AuthorizationService authorization;

    public RetentionManagementService(RetentionManagementStore store, FeatureEntitlementService features,
                                       AuthorizationService authorization) {
        this.store = store;
        this.features = features;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public RetentionPolicySummary policy(UUID tenantId) {
        require(tenantId, "retention.read");
        return store.findPolicy(tenantId);
    }

    @Transactional
    public RetentionPolicySummary savePolicy(UUID tenantId, UUID actorId, RetentionPolicyCommand command) {
        require(tenantId, "retention.manage");
        return store.savePolicy(tenantId, actorId, normalize(command));
    }

    @Transactional(readOnly = true)
    public RetentionCleanupSummary preview(UUID tenantId) {
        require(tenantId, "retention.read");
        return store.preview(tenantId);
    }

    @Transactional
    public RetentionCleanupSummary cleanup(UUID tenantId, UUID actorId) {
        require(tenantId, "retention.execute");
        RetentionPolicySummary policy = store.findPolicy(tenantId);
        if (!policy.enabled() || "PREVIEW_ONLY".equals(policy.operationMode())) {
            throw new IllegalArgumentException("retention cleanup is not enabled for this tenant");
        }
        return store.cleanup(tenantId, actorId);
    }

    @Transactional(readOnly = true)
    public List<RetentionHoldSummary> holds(UUID tenantId) {
        require(tenantId, "retention.read");
        return store.findHolds(tenantId);
    }

    @Transactional
    public RetentionHoldSummary createHold(UUID tenantId, UUID actorId, String holdKey, String reason) {
        require(tenantId, "retention.manage");
        return store.createHold(tenantId, actorId, required(holdKey, "holdKey", 120), required(reason, "reason", 1000));
    }

    @Transactional
    public RetentionHoldSummary releaseHold(UUID tenantId, UUID actorId, UUID holdId) {
        require(tenantId, "retention.manage");
        if (holdId == null) throw new IllegalArgumentException("holdId is required");
        return store.releaseHold(tenantId, actorId, holdId);
    }

    private void require(UUID tenantId, String permission) {
        features.requireEnabled(tenantId, "RETENTION_MANAGEMENT");
        authorization.requirePermission(permission);
    }

    private static RetentionPolicyCommand normalize(RetentionPolicyCommand command) {
        if (command == null) throw new IllegalArgumentException("request body is required");
        String mode = command.operationMode() == null ? "PREVIEW_ONLY" : command.operationMode().trim().toUpperCase(Locale.ROOT);
        if (!MODES.contains(mode)) throw new IllegalArgumentException("operationMode must be PURGE or PREVIEW_ONLY");
        if (command.activityRetentionDays() < 120 || command.activityRetentionDays() > 3650)
            throw new IllegalArgumentException("activityRetentionDays must be between 120 and 3650");
        if (command.notificationRetentionDays() < 30 || command.notificationRetentionDays() > 3650)
            throw new IllegalArgumentException("notificationRetentionDays must be between 30 and 3650");
        if (command.deliveryRetentionDays() < 30 || command.deliveryRetentionDays() > 3650)
            throw new IllegalArgumentException("deliveryRetentionDays must be between 30 and 3650");
        if (command.auditRetentionDays() < 365 || command.auditRetentionDays() > 3650)
            throw new IllegalArgumentException("auditRetentionDays must be between 365 and 3650");
        return new RetentionPolicyCommand(command.enabled(), mode, command.activityRetentionDays(),
                command.notificationRetentionDays(), command.deliveryRetentionDays(), command.auditRetentionDays());
    }

    private static String required(String value, String field, int max) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " is required");
        if (normalized.length() > max) throw new IllegalArgumentException(field + " is too long");
        return normalized;
    }
}
