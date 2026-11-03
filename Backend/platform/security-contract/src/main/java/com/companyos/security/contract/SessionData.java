package com.companyos.security.contract;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record SessionData(
        UUID tenantId,
        UUID userId,
        String tenantKey,
        String email,
        String displayName,
        Set<String> authorities,
        Instant expiresAt) {
}
