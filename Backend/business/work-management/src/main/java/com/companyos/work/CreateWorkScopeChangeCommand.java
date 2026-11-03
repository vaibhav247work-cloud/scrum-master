package com.companyos.work;

import java.util.UUID;

public record CreateWorkScopeChangeCommand(
        UUID workItemId,
        String requestedPriority,
        UUID targetSprintId,
        boolean emergency,
        String reason) {
}
