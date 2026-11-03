package com.companyos.demand;

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
public class DemandManagementService {
    private static final Pattern KEY = Pattern.compile("[A-Z][A-Z0-9_-]{1,31}");
    private static final Set<String> TYPES = Set.of("CLIENT", "INTERNAL", "IDEA", "INCIDENT");
    private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "URGENT");
    private static final Set<String> DECISIONS = Set.of("IN_REVIEW", "APPROVED", "REJECTED", "DEFERRED");
    private final DemandStore store;
    private final FeatureEntitlementService featureEntitlements;
    private final AuthorizationService authorization;

    public DemandManagementService(DemandStore store, FeatureEntitlementService featureEntitlements,
                                   AuthorizationService authorization) {
        this.store = store;
        this.featureEntitlements = featureEntitlements;
        this.authorization = authorization;
    }

    @Transactional
    public DemandSummary create(UUID tenantId, UUID actorId, CreateDemandCommand command) {
        requireFeature(tenantId);
        authorization.requirePermission("demand.create");
        return store.create(tenantId, actorId, normalize(command));
    }

    @Transactional(readOnly = true)
    public List<DemandSummary> list(UUID tenantId, String status) {
        requireFeature(tenantId);
        authorization.requirePermission("demand.read");
        String normalizedStatus = status == null || status.isBlank() ? null : normalizeEnum(status, "status", DemandStatus.class);
        return store.findAll(tenantId, normalizedStatus);
    }

    @Transactional
    public DemandSummary decide(UUID tenantId, UUID actorId, UUID demandId, String status,
                                UUID ownerUserId, String note) {
        requireFeature(tenantId);
        authorization.requirePermission("demand.approve");
        if (demandId == null) throw new IllegalArgumentException("demandId is required");
        String normalizedStatus = normalizeEnum(status, "status", DECISIONS);
        String normalizedNote = note == null ? "" : note.trim();
        if (normalizedNote.length() > 2000) throw new IllegalArgumentException("note must be at most 2000 characters");
        return store.decide(tenantId, demandId, actorId, normalizedStatus, ownerUserId, normalizedNote);
    }

    private void requireFeature(UUID tenantId) {
        featureEntitlements.requireEnabled(tenantId, "DEMAND_INTAKE");
    }

    private static CreateDemandCommand normalize(CreateDemandCommand command) {
        if (command == null) throw new IllegalArgumentException("request body is required");
        String key = required(command.demandKey(), "demandKey", 32).toUpperCase(Locale.ROOT);
        if (!KEY.matcher(key).matches()) throw new IllegalArgumentException("demandKey is invalid");
        String title = required(command.title(), "title", 240);
        String description = command.description() == null ? "" : command.description().trim();
        if (description.length() > 8000) throw new IllegalArgumentException("description must be at most 8000 characters");
        return new CreateDemandCommand(key, title, description,
                normalizeEnum(command.demandType(), "demandType", TYPES, "INTERNAL"),
                normalizeEnum(command.priority(), "priority", PRIORITIES, "MEDIUM"));
    }

    private static String required(String value, String field, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        if (normalized.length() > maxLength) throw new IllegalArgumentException(field + " must be at most " + maxLength + " characters");
        return normalized;
    }

    private static String normalizeEnum(String value, String field, Set<String> allowed) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new IllegalArgumentException(field + " is invalid");
        return normalized;
    }

    private static <E extends Enum<E>> String normalizeEnum(String value, String field, Class<E> type) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        try { return Enum.valueOf(type, normalized).name(); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException(field + " is invalid"); }
    }

    private static String normalizeEnum(String value, String field, Set<String> allowed, String defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        return normalizeEnum(value, field, allowed);
    }
}
