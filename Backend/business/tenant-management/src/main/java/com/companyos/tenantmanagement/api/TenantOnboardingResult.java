package com.companyos.tenantmanagement.api;

import java.util.Set;
import java.util.UUID;

public record TenantOnboardingResult(
        UUID tenantId,
        UUID ownerId,
        String tenantKey,
        String ownerEmail,
        Set<String> enabledFeatures,
        String tenantStatus,
        String ownerStatus) {
}
