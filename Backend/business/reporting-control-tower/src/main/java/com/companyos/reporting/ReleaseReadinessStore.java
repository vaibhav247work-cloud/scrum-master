package com.companyos.reporting;

import java.util.List;
import java.util.UUID;

public interface ReleaseReadinessStore {
    List<ReleaseReadinessRow> readiness(UUID tenantId, UUID projectId);
}
