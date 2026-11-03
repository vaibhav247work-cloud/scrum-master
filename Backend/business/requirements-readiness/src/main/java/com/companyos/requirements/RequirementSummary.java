package com.companyos.requirements;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RequirementSummary(
        UUID id,
        String requirementKey,
        UUID demandId,
        String title,
        String description,
        String businessContext,
        String uxContext,
        String technicalContext,
        String acceptanceCriteria,
        String status,
        UUID createdBy,
        UUID reviewedBy,
        String reviewNote,
        Instant reviewedAt,
        Instant createdAt,
        Instant updatedAt,
        List<ReadinessCheck> checks) {
}
