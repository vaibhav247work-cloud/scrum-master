package com.companyos.integration;

import java.time.Instant;

public record IngestIntegrationEventCommand(String sourceSystem, String eventType, String externalId,
                                            String repository, String branch, String environment, String sourceUrl,
                                            Instant occurredAt, String payload) { }
