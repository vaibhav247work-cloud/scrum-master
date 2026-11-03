package com.companyos.sprint;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SprintSummary(
        UUID id,
        UUID projectId,
        String sprintKey,
        String name,
        String goal,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        Instant createdAt) {
}
