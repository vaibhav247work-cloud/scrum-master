package com.companyos.quality;

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
public class QualityOperationsService {
    private static final Pattern KEY = Pattern.compile("[A-Z][A-Z0-9_]{1,31}");
    private static final Set<String> GATE_TYPES = Set.of("QA", "SECURITY", "COMPLIANCE", "APPROVAL");
    private static final Set<String> GATE_STATUSES = Set.of("PASSED", "FAILED", "WAIVED");
    private static final Set<String> SEVERITIES = Set.of("S1", "S2", "S3", "S4");
    private static final Set<String> INCIDENT_STATUSES = Set.of("OPEN", "INVESTIGATING", "MITIGATED", "RESOLVED", "CLOSED");
    private final QualityOperationsStore store;
    private final FeatureEntitlementService features;
    private final AuthorizationService authorization;

    public QualityOperationsService(QualityOperationsStore store, FeatureEntitlementService features,
                                    AuthorizationService authorization) {
        this.store = store;
        this.features = features;
        this.authorization = authorization;
    }

    @Transactional
    public QualityGateSummary createGate(UUID tenantId, UUID actorId, CreateQualityGateCommand command) {
        features.requireEnabled(tenantId, "QUALITY_GATES");
        authorization.requirePermission("quality.manage");
        if (command == null || (command.projectId() == null && command.releaseId() == null)) throw new IllegalArgumentException("projectId or releaseId is required");
        String key = key(command.gateKey(), "gateKey");
        String type = enumValue(command.gateType(), "gateType", GATE_TYPES, "QA");
        return store.createGate(tenantId, actorId, new CreateQualityGateCommand(command.projectId(), command.releaseId(), key, type, command.required()));
    }

    @Transactional(readOnly = true)
    public List<QualityGateSummary> listGates(UUID tenantId, UUID projectId, UUID releaseId) {
        features.requireEnabled(tenantId, "QUALITY_GATES");
        authorization.requirePermission("quality.read");
        return store.findGates(tenantId, projectId, releaseId);
    }

    @Transactional
    public QualityGateSummary decideGate(UUID tenantId, UUID actorId, UUID gateId, String status, String evidence) {
        features.requireEnabled(tenantId, "QUALITY_GATES");
        String normalized = enumValue(status, "status", GATE_STATUSES, null);
        authorization.requirePermission("WAIVED".equals(normalized) ? "quality.waive" : "quality.manage");
        return store.decideGate(tenantId, actorId, requiredId(gateId, "gateId"), normalized, bounded(evidence, "evidence", 4000));
    }

    @Transactional
    public IncidentSummary createIncident(UUID tenantId, UUID actorId, CreateIncidentCommand command) {
        features.requireEnabled(tenantId, "OPERATIONS_INCIDENTS");
        authorization.requirePermission("incident.manage");
        if (command == null) throw new IllegalArgumentException("request body is required");
        if (command.projectId() == null && command.releaseId() == null) throw new IllegalArgumentException("projectId or releaseId is required");
        return store.createIncident(tenantId, actorId, new CreateIncidentCommand(command.projectId(), command.releaseId(),
                key(command.incidentKey(), "incidentKey"), required(command.title(), "title", 240),
                bounded(command.description(), "description", 8000), enumValue(command.severity(), "severity", SEVERITIES, "S3"), command.ownerUserId()));
    }

    @Transactional(readOnly = true)
    public List<IncidentSummary> listIncidents(UUID tenantId, String status) {
        features.requireEnabled(tenantId, "OPERATIONS_INCIDENTS");
        authorization.requirePermission("incident.read");
        return store.findIncidents(tenantId, status == null || status.isBlank() ? null : enumValue(status, "status", INCIDENT_STATUSES, null));
    }

    @Transactional
    public IncidentSummary updateIncident(UUID tenantId, UUID actorId, UUID incidentId, String status, String mitigation, String rootCause) {
        features.requireEnabled(tenantId, "OPERATIONS_INCIDENTS");
        authorization.requirePermission("incident.manage");
        return store.updateIncident(tenantId, actorId, requiredId(incidentId, "incidentId"), enumValue(status, "status", INCIDENT_STATUSES, null), bounded(mitigation, "mitigation", 8000), bounded(rootCause, "rootCause", 8000));
    }

    private static String key(String value, String field) { String normalized = required(value, field, 32).toUpperCase(Locale.ROOT).replace('-', '_'); if (!KEY.matcher(normalized).matches()) throw new IllegalArgumentException(field + " is invalid"); return normalized; }
    private static String required(String value, String field, int max) { String normalized = value == null ? "" : value.trim(); if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required"); if (normalized.length() > max) throw new IllegalArgumentException(field + " must be at most " + max + " characters"); return normalized; }
    private static String bounded(String value, String field, int max) { String normalized = value == null ? "" : value.trim(); if (normalized.length() > max) throw new IllegalArgumentException(field + " must be at most " + max + " characters"); return normalized; }
    private static String enumValue(String value, String field, Set<String> allowed, String fallback) { String normalized = value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT); if (normalized == null || !allowed.contains(normalized)) throw new IllegalArgumentException(field + " is invalid"); return normalized; }
    private static UUID requiredId(UUID value, String field) { if (value == null) throw new IllegalArgumentException(field + " is required"); return value; }
}
