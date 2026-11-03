package com.companyos.workflow;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkflowSummary(
        UUID id,
        String workflowKey,
        String name,
        String methodology,
        String status,
        List<WorkflowStatusDefinition> statuses,
        List<WorkflowTransitionDefinition> transitions,
        Instant createdAt) { }
