package com.companyos.identity.access;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record TenantUserInvitationData(
        UUID invitationId,
        UUID tenantId,
        UUID userId,
        String email,
        String displayName,
        String tokenHash,
        Instant expiresAt,
        UUID createdBy,
        Set<String> roleKeys,
        Instant createdAt) {
}
