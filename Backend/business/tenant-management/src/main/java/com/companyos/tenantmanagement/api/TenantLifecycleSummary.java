package com.companyos.tenantmanagement.api;

import java.time.Instant;
import java.util.UUID;

public record TenantLifecycleSummary(
        UUID tenantId,
        String tenantKey,
        String displayName,
        String status,
        Instant suspendedAt,
        Instant deletionScheduledAt,
        Instant updatedAt) {
}
