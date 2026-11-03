package com.companyos.work;

import java.util.UUID;

public record UpdateWorkItemCommand(
        String title,
        String description,
        UUID assigneeUserId,
        String workType,
        String priority,
        String status,
        UUID teamId,
        String assignmentReason,
        Integer estimateMinutes,
        UUID parentWorkItemId,
        String acceptanceCriteria) {
    public UpdateWorkItemCommand(String title, String description, UUID assigneeUserId, String workType,
                                  String priority, String status) {
        this(title, description, assigneeUserId, workType, priority, status, null, "", null, null, "");
    }

    public UpdateWorkItemCommand(String title, String description, UUID assigneeUserId, String workType,
                                 String priority, String status, UUID teamId) {
        this(title, description, assigneeUserId, workType, priority, status, teamId, "", null, null, "");
    }

    public UpdateWorkItemCommand(String title, String description, UUID assigneeUserId, String workType,
                                 String priority, String status, UUID teamId, String assignmentReason) {
        this(title, description, assigneeUserId, workType, priority, status, teamId, assignmentReason, null, null, "");
    }
}
