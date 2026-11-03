package com.companyos.tenantmanagement.api;

import java.util.Set;

public record TenantOnboardingCommand(
        String tenantKey,
        String legalName,
        String displayName,
        String timezone,
        String ownerEmail,
        String ownerDisplayName,
        String ownerPassword,
        Set<String> enabledFeatures) {
}
