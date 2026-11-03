package com.companyos.organization;

import java.time.Instant;
import java.util.UUID;

public record OrganizationUnitSummary(
        UUID id, UUID parentUnitId, String unitKey, String name, String unitType,
        UUID managerUserId, String status, Instant createdAt) {
}
