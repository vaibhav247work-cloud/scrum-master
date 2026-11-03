package com.companyos.resource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ResourceActivity(
        UUID timeEntryId,
        UUID workItemId,
        UUID projectId,
        UUID sprintId,
        LocalDate entryDate,
        int minutes,
        String description,
        String source,
        Instant createdAt) {
}
