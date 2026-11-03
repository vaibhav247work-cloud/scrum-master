package com.companyos.workflow;

import java.util.UUID;

public interface WorkflowPolicy {
    void validateTransition(UUID tenantId, UUID projectId, String fromStatus, String toStatus);

    static WorkflowPolicy allowAll() {
        return (tenantId, projectId, fromStatus, toStatus) -> { };
    }
}
