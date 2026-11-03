package com.companyos.time;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TimeEntrySummary(
        UUID id,
        UUID userId,
        UUID workItemId,
        UUID projectId,
        UUID sprintId,
        LocalDate entryDate,
        int minutes,
        String description,
        String source,
        String categoryKey,
        Instant createdAt,
        String approvalStatus,
        UUID approvedBy,
        Instant approvedAt,
        String approvalNote) {

    public TimeEntrySummary(UUID id, UUID userId, UUID workItemId, UUID projectId, UUID sprintId,
                            LocalDate entryDate, int minutes, String description, String source,
                            Instant createdAt) {
        this(id, userId, workItemId, projectId, sprintId, entryDate, minutes, description, source,
                "GENERAL", createdAt, "APPROVED", null, null, "");
    }
}
