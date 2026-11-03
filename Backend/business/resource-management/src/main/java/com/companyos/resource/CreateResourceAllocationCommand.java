package com.companyos.resource;

import java.time.LocalDate;
import java.util.UUID;

public record CreateResourceAllocationCommand(
        UUID userId,
        UUID projectId,
        UUID sprintId,
        UUID teamId,
        LocalDate startDate,
        LocalDate endDate,
        int plannedMinutesPerWeek,
        String note) {
}
