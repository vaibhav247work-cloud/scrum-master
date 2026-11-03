package com.companyos.requirements;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.security.contract.AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class RequirementsReadinessService {
    private static final Pattern KEY = Pattern.compile("[A-Z][A-Z0-9_-]{1,31}");
    private static final Set<String> DECISIONS = Set.of("IN_REVIEW", "CHANGES_REQUESTED", "READY", "REJECTED");
    private final RequirementsStore store;
    private final FeatureEntitlementService featureEntitlements;
    private final AuthorizationService authorization;

    public RequirementsReadinessService(RequirementsStore store, FeatureEntitlementService featureEntitlements,
                                        AuthorizationService authorization) {
        this.store = store;
        this.featureEntitlements = featureEntitlements;
        this.authorization = authorization;
    }

    @Transactional
    public RequirementSummary create(UUID tenantId, UUID actorId, CreateRequirementCommand command) {
        requireFeature(tenantId);
        authorization.requirePermission("requirement.create");
        return store.create(tenantId, actorId, normalize(command));
    }

    @Transactional(readOnly = true)
    public List<RequirementSummary> list(UUID tenantId, String status) {
        requireFeature(tenantId);
        authorization.requirePermission("requirement.read");
        String normalized = status == null || status.isBlank() ? null : normalizeStatus(status);
        return store.findAll(tenantId, normalized);
    }

    @Transactional
    public RequirementSummary updateCheck(UUID tenantId, UUID requirementId, String checkKey, String label,
                                          boolean required, boolean completed, String evidence, UUID actorId) {
        requireFeature(tenantId);
        authorization.requirePermission("requirement.manage");
        String key = required(checkKey, "checkKey", 64).toUpperCase(Locale.ROOT);
        String normalizedLabel = required(label, "label", 240);
        String normalizedEvidence = evidence == null ? "" : evidence.trim();
        if (normalizedEvidence.length() > 2000) throw new IllegalArgumentException("evidence must be at most 2000 characters");
        return store.updateCheck(tenantId, requirementId, key, normalizedLabel, required, completed, normalizedEvidence, actorId);
    }

    @Transactional
    public RequirementSummary decide(UUID tenantId, UUID requirementId, UUID reviewerId, String status, String note) {
        requireFeature(tenantId);
        authorization.requirePermission("requirement.review");
        String normalizedStatus = normalizeEnum(status, "status", DECISIONS);
        String normalizedNote = note == null ? "" : note.trim();
        if (normalizedNote.length() > 2000) throw new IllegalArgumentException("note must be at most 2000 characters");
        return store.decide(tenantId, requirementId, reviewerId, normalizedStatus, normalizedNote);
    }

    private void requireFeature(UUID tenantId) { featureEntitlements.requireEnabled(tenantId, "REQUIREMENTS_READINESS"); }

    private static CreateRequirementCommand normalize(CreateRequirementCommand command) {
        if (command == null) throw new IllegalArgumentException("request body is required");
        String key = required(command.requirementKey(), "requirementKey", 32).toUpperCase(Locale.ROOT);
        if (!KEY.matcher(key).matches()) throw new IllegalArgumentException("requirementKey is invalid");
        return new CreateRequirementCommand(key, command.demandId(), required(command.title(), "title", 240),
                text(command.description(), "description", 8000), text(command.businessContext(), "businessContext", 8000),
                text(command.uxContext(), "uxContext", 8000), text(command.technicalContext(), "technicalContext", 8000),
                text(command.acceptanceCriteria(), "acceptanceCriteria", 8000));
    }

    private static String text(String value, String field, int max) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " must be at most " + max + " characters");
        return normalized;
    }

    private static String required(String value, String field, int max) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        if (normalized.length() > max) throw new IllegalArgumentException(field + " must be at most " + max + " characters");
        return normalized;
    }

    private static String normalizeStatus(String value) { return normalizeEnum(value, "status", Set.of("DRAFT", "IN_REVIEW", "CHANGES_REQUESTED", "READY", "REJECTED")); }
    private static String normalizeEnum(String value, String field, Set<String> allowed) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new IllegalArgumentException(field + " is invalid");
        return normalized;
    }
}
