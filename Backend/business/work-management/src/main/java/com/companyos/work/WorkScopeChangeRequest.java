package com.companyos.work;

import java.time.Instant;
import java.util.UUID;

public record WorkScopeChangeRequest(
        UUID id,
        UUID workItemId,
        UUID projectId,
        String requestedPriority,
        UUID targetSprintId,
        UUID requestedBy,
        boolean emergency,
        String reason,
        String status,
        UUID decidedBy,
        String decisionNote,
        Instant createdAt,
        Instant decidedAt) {
}
