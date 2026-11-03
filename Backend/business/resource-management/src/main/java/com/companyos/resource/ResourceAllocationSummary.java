package com.companyos.resource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ResourceAllocationSummary(
        UUID id,
        UUID userId,
        UUID projectId,
        UUID sprintId,
        UUID teamId,
        LocalDate startDate,
        LocalDate endDate,
        int plannedMinutesPerWeek,
        String status,
        String note,
        Instant createdAt) {
}
