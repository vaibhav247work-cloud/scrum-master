package com.companyos.work;

import java.time.Instant;
import java.util.UUID;

public record WorkItemSummary(
        UUID id,
        String workKey,
        UUID projectId,
        UUID sprintId,
        UUID assigneeUserId,
        String title,
        String description,
        String workType,
        String priority,
        String status,
        Instant createdAt,
        UUID teamId,
        Integer estimateMinutes,
        Instant completedAt,
        UUID parentWorkItemId,
        String acceptanceCriteria,
        Long backlogPosition) {
    public WorkItemSummary(UUID id, String workKey, UUID projectId, UUID sprintId, UUID assigneeUserId,
                           String title, String description, String workType, String priority, String status,
                           Instant createdAt) {
        this(id, workKey, projectId, sprintId, assigneeUserId, title, description, workType, priority, status,
                createdAt, null, null, null, null, "", null);
    }

    public WorkItemSummary(UUID id, String workKey, UUID projectId, UUID sprintId, UUID assigneeUserId,
                           String title, String description, String workType, String priority, String status,
                           Instant createdAt, UUID teamId) {
        this(id, workKey, projectId, sprintId, assigneeUserId, title, description, workType, priority, status,
                createdAt, teamId, null, null, null, "", null);
    }

    public WorkItemSummary(UUID id, String workKey, UUID projectId, UUID sprintId, UUID assigneeUserId,
                           String title, String description, String workType, String priority, String status,
                           Instant createdAt, UUID teamId, Integer estimateMinutes) {
        this(id, workKey, projectId, sprintId, assigneeUserId, title, description, workType, priority, status,
                createdAt, teamId, estimateMinutes, null, null, "", null);
    }
}
