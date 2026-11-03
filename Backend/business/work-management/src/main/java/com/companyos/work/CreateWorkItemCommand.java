package com.companyos.work;

import java.util.UUID;

public record CreateWorkItemCommand(
        UUID projectId,
        UUID sprintId,
        UUID assigneeUserId,
        String title,
        String description,
        String workType,
        String priority,
        UUID teamId,
        Integer estimateMinutes,
        UUID parentWorkItemId,
        String acceptanceCriteria) {
    public CreateWorkItemCommand(UUID projectId, UUID sprintId, UUID assigneeUserId, String title,
                                 String description, String workType, String priority) {
        this(projectId, sprintId, assigneeUserId, title, description, workType, priority, null, null, null, "");
    }

    public CreateWorkItemCommand(UUID projectId, UUID sprintId, UUID assigneeUserId, String title,
                                 String description, String workType, String priority, UUID teamId) {
        this(projectId, sprintId, assigneeUserId, title, description, workType, priority, teamId, null, null, "");
    }

    public CreateWorkItemCommand(UUID projectId, UUID sprintId, UUID assigneeUserId, String title,
                                 String description, String workType, String priority, UUID teamId,
                                 Integer estimateMinutes) {
        this(projectId, sprintId, assigneeUserId, title, description, workType, priority, teamId, estimateMinutes, null, "");
    }
}
