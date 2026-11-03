package com.companyos.tenantmanagement.api;

import java.util.Map;
import java.util.UUID;

public interface TenantLifecycleStore {
    TenantLifecycleSummary find(UUID tenantId);

    TenantLifecycleSummary updateStatus(UUID tenantId, UUID actorId, String status, String reason);

    Map<String, Object> export(UUID tenantId);
}
