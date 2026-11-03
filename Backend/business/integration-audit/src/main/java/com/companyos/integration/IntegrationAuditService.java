package com.companyos.integration;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.security.contract.AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class IntegrationAuditService {
    private static final Set<String> TYPES = Set.of("GITHUB", "GITLAB", "CI_CD", "WEBHOOK");
    private static final Set<String> CI_EVENTS = Set.of("BUILD_STARTED", "BUILD_SUCCEEDED", "BUILD_FAILED", "DEPLOYMENT_STARTED", "DEPLOYMENT_SUCCEEDED", "DEPLOYMENT_FAILED", "ENVIRONMENT_CHANGED", "ROLLBACK");
    private final IntegrationAuditStore store;
    private final FeatureEntitlementService features;
    private final AuthorizationService authorization;

    public IntegrationAuditService(IntegrationAuditStore store, FeatureEntitlementService features, AuthorizationService authorization) {
        this.store = store;
        this.features = features;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public List<IntegrationConnectionSummary> connections(UUID tenantId) {
        requireFeature(tenantId);
        authorization.requirePermission("integration.connection.read");
        return store.findConnections(tenantId);
    }

    @Transactional
    public IntegrationConnectionSummary createConnection(UUID tenantId, UUID actorId, CreateIntegrationConnectionCommand command) {
        requireFeature(tenantId);
        authorization.requirePermission("integration.connection.manage");
        if (command == null) throw new IllegalArgumentException("request body is required");
        String type = enumValue(command.connectionType(), "connectionType", TYPES);
        String name = required(command.name(), "name", 200);
        String baseUrl = bounded(command.baseUrl(), "baseUrl", 1000);
        String account = bounded(command.externalAccount(), "externalAccount", 240);
        String secret = required(command.secret(), "secret", 4000);
        return store.createConnection(tenantId, actorId, new CreateIntegrationConnectionCommand(type, name, baseUrl, account, secret), Instant.now());
    }

    @Transactional
    public ExternalIntegrationEventSummary ingestEvent(UUID tenantId, UUID connectionId, String suppliedSecret,
                                                        IngestIntegrationEventCommand command) {
        requireFeature(tenantId);
        authorization.requirePermission("integration.event.ingest");
        if (connectionId == null) throw new IllegalArgumentException("connectionId is required");
        NormalizedEvent normalized = normalizeEvent(command);
        return store.ingestEvent(tenantId, connectionId, required(suppliedSecret, "integration secret", 4000),
                normalized.command(), Instant.now());
    }

    @Transactional
    public ExternalIntegrationEventSummary ingestSignedEvent(UUID connectionId, String signature, String timestamp,
                                                              String rawBody, IngestIntegrationEventCommand command) {
        if (connectionId == null) throw new IllegalArgumentException("connectionId is required");
        UUID tenantId = store.tenantIdForConnection(connectionId);
        if (tenantId == null) throw new IllegalArgumentException("integration connection was not found");
        requireFeature(tenantId);
        NormalizedEvent normalized = normalizeEvent(command);
        return store.ingestSignedEvent(connectionId, required(signature, "integration signature", 200),
                required(timestamp, "integration timestamp", 40), required(rawBody, "request body", 20000),
                normalized.command(), Instant.now());
    }

    private NormalizedEvent normalizeEvent(IngestIntegrationEventCommand command) {
        if (command == null) throw new IllegalArgumentException("request body is required");
        String source = enumValue(command.sourceSystem(), "sourceSystem", TYPES);
        String eventType = command.eventType() == null ? "" : command.eventType().trim().toUpperCase(Locale.ROOT);
        if (eventType.isBlank() || eventType.length() > 100) throw new IllegalArgumentException("eventType is invalid");
        if ("CI_CD".equals(source) && !CI_EVENTS.contains(eventType)) throw new IllegalArgumentException("CI/CD eventType is not supported");
        return new NormalizedEvent(new IngestIntegrationEventCommand(source, eventType,
                required(command.externalId(), "externalId", 240), bounded(command.repository(), "repository", 500),
                bounded(command.branch(), "branch", 500), bounded(command.environment(), "environment", 200),
                bounded(command.sourceUrl(), "sourceUrl", 2000), command.occurredAt() == null ? Instant.now() : command.occurredAt(),
                bounded(command.payload(), "payload", 20000)));
    }

    private record NormalizedEvent(IngestIntegrationEventCommand command) { }

    private void requireFeature(UUID tenantId) { features.requireEnabled(tenantId, "INTEGRATIONS"); }
    private static String enumValue(String value, String field, Set<String> allowed) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new IllegalArgumentException(field + " is invalid");
        return normalized;
    }
    private static String required(String value, String field, int max) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " is required");
        if (normalized.length() > max) throw new IllegalArgumentException(field + " must be at most " + max + " characters");
        return normalized;
    }
    private static String bounded(String value, String field, int max) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " must be at most " + max + " characters");
        return normalized;
    }
}
