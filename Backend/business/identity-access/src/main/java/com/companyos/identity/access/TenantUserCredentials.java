package com.companyos.identity.access;

import java.util.Set;
import java.util.UUID;

public record TenantUserCredentials(
        UUID tenantId,
        UUID userId,
        String tenantKey,
        String email,
        String displayName,
        String passwordHash,
        String status,
        Set<String> authorities) {
}
