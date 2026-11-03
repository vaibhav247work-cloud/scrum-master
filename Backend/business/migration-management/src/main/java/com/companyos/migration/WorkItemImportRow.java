package com.companyos.migration;

import java.util.UUID;

public record WorkItemImportRow(String sourceKey, UUID projectId, UUID sprintId, UUID assigneeUserId,
                                String title, String description, String workType, String priority,
                                UUID teamId, Integer estimateMinutes, String acceptanceCriteria) { }
