package com.companyos.tenantmanagement.port;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Immutable data passed from the onboarding use case to a persistence adapter. */
public record TenantOnboardingData(
        UUID tenantId,
        String tenantKey,
        String legalName,
        String displayName,
        String timezone,
        UUID ownerId,
        String ownerEmail,
        String ownerDisplayName,
        String ownerPasswordHash,
        UUID initialRoleId,
        String initialRoleKey,
        Set<String> initialRolePermissions,
        Set<String> enabledFeatures,
        Instant createdAt) {
}
