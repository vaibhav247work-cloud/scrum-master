package com.companyos.reporting;

import java.time.Instant;
import java.util.UUID;

public interface ForecastPolicyStore {
    ForecastPolicy find(UUID tenantId);
    ForecastPolicy save(UUID tenantId, UUID actorId, ForecastPolicy policy, Instant updatedAt);
}
