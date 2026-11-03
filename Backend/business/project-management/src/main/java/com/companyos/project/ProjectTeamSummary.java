package com.companyos.project;

import java.time.Instant;
import java.util.UUID;

public record ProjectTeamSummary(
        UUID projectId,
        UUID teamId,
        String relationshipRole,
        Instant createdAt) {
}
