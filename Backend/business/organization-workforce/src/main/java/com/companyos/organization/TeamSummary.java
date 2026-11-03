package com.companyos.organization;

import java.time.Instant;
import java.util.UUID;

public record TeamSummary(
        UUID id, UUID parentUnitId, String teamKey, String name, UUID managerUserId,
        String status, int memberCount, Instant createdAt) {
}
