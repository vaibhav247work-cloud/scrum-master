package com.companyos.work;

import java.util.UUID;

public record WorkItemFilter(UUID projectId, UUID sprintId, UUID assigneeUserId, UUID teamId) {
    public WorkItemFilter(UUID projectId, UUID sprintId, UUID assigneeUserId) {
        this(projectId, sprintId, assigneeUserId, null);
    }
}
