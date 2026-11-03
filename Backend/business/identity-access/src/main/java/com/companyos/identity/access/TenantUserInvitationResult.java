package com.companyos.identity.access;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record TenantUserInvitationResult(
        UUID invitationId,
        UUID userId,
        String email,
        Instant expiresAt,
        Set<String> roleKeys,
        String rawToken) {
}
