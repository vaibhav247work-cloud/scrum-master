package com.companyos.organization;

import java.time.Instant;
import java.util.UUID;

public record TeamMemberSummary(
        UUID userId, String email, String displayName, String membershipRole,
        String status, Instant joinedAt, Instant leftAt) {
}
