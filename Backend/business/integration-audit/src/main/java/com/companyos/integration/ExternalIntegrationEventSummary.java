package com.companyos.integration;

import java.time.Instant;
import java.util.UUID;

public record ExternalIntegrationEventSummary(UUID id, UUID connectionId, String sourceSystem, String eventType,
                                              String externalId, String status, String repository, String branch,
                                              String environment, String sourceUrl, Instant occurredAt,
                                              Instant receivedAt, boolean duplicate) { }
