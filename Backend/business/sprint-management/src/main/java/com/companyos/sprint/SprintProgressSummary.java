package com.companyos.sprint;

import java.util.UUID;

public record SprintProgressSummary(
        UUID sprintId,
        int totalWorkItems,
        int todoWorkItems,
        int inProgressWorkItems,
        int blockedWorkItems,
        int completedWorkItems,
        int cancelledWorkItems,
        int carryOverWorkItems) {
}
