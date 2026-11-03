package com.companyos.demand;

import java.time.Instant;
import java.util.UUID;

public record DemandSummary(
        UUID id,
        String demandKey,
        String title,
        String description,
        String demandType,
        String priority,
        String status,
        UUID requestedBy,
        UUID ownerUserId,
        UUID decidedBy,
        String decisionNote,
        Instant decidedAt,
        Instant createdAt,
        Instant updatedAt) {
}
