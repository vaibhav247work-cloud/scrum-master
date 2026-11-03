package com.companyos.integration;

import java.time.Instant;
import java.util.UUID;

public record IntegrationConnectionSummary(UUID id, String connectionType, String name, String status,
                                           String baseUrl, String externalAccount, boolean secretConfigured,
                                           Instant lastEventAt, String lastError, Instant createdAt, Instant updatedAt) { }
