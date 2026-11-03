package com.companyos.work;

import java.time.Instant;
import java.util.UUID;

public record WorkItemAssignmentHistory(
        UUID id,
        UUID workItemId,
        UUID previousAssigneeUserId,
        UUID newAssigneeUserId,
        UUID changedBy,
        String reason,
        Instant changedAt) {
}
