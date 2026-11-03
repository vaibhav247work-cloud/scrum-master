package com.companyos.workflow;

import java.util.List;

public record CreateWorkflowCommand(
        String workflowKey,
        String name,
        String methodology,
        List<WorkflowStatusDefinition> statuses,
        List<WorkflowTransitionDefinition> transitions) { }
