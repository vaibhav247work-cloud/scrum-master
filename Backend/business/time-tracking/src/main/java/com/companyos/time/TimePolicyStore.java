package com.companyos.time;

import java.time.Instant;
import java.util.UUID;

public interface TimePolicyStore {
    TimePolicy find(UUID tenantId);

    TimePolicy save(UUID tenantId, TimePolicy policy, Instant updatedAt);
}
