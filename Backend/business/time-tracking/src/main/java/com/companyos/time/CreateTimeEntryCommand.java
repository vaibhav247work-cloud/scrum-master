package com.companyos.time;

import java.time.LocalDate;
import java.util.UUID;

public record CreateTimeEntryCommand(
        UUID workItemId,
        UUID projectId,
        UUID sprintId,
        LocalDate entryDate,
        int minutes,
        String description,
        String source,
        String categoryKey,
        String approvalStatus) {

    public CreateTimeEntryCommand(UUID workItemId, UUID projectId, UUID sprintId,
                                  LocalDate entryDate, int minutes, String description, String source) {
        this(workItemId, projectId, sprintId, entryDate, minutes, description, source, "GENERAL", "APPROVED");
    }
}
