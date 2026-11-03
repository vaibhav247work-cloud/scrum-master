package com.companyos.identity.access;

import java.util.UUID;

public record TenantUserSummary(
        UUID userId,
        String email,
        String displayName,
        String status) {
}
