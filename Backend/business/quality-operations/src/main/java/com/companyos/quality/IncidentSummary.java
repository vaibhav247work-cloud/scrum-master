package com.companyos.quality;

import java.time.Instant;
import java.util.UUID;

public record IncidentSummary(UUID id, UUID projectId, UUID releaseId, String incidentKey, String title,
                              String description, String severity, String status, UUID ownerUserId,
                              String mitigation, String rootCause, Instant openedAt, Instant updatedAt) { }
