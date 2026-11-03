package com.companyos.resource;

import java.time.Instant;
import java.util.UUID;

public record ResourceAssignedWork(
        UUID workItemId,
        String workKey,
        UUID projectId,
        UUID sprintId,
        String title,
        String workType,
        String priority,
        String status,
        Instant updatedAt) {
}
