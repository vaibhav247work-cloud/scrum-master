package com.companyos.sprint;

import java.time.Instant;
import java.util.UUID;

public record SprintTeamSummary(
        UUID sprintId,
        UUID teamId,
        String relationshipRole,
        Instant createdAt) {
}
