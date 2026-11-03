package com.companyos.integration;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface IntegrationAuditStore {
    List<IntegrationConnectionSummary> findConnections(UUID tenantId);
    IntegrationConnectionSummary createConnection(UUID tenantId, UUID actorId, CreateIntegrationConnectionCommand command, Instant now);
    ExternalIntegrationEventSummary ingestEvent(UUID tenantId, UUID connectionId, String suppliedSecret,
                                                 IngestIntegrationEventCommand command, Instant receivedAt);

    ExternalIntegrationEventSummary ingestSignedEvent(UUID connectionId, String signature, String timestamp,
                                                      String rawBody, IngestIntegrationEventCommand command,
                                                      Instant receivedAt);

    UUID tenantIdForConnection(UUID connectionId);
}
