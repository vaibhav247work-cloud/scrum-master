package com.companyos.sprint;

import java.util.UUID;

public record SprintCarryOverResult(
        UUID sourceSprintId,
        UUID targetSprintId,
        int movedWorkItemCount,
        int remainingWorkItemCount) {
}
