package com.companyos.demand;

import java.util.List;
import java.util.UUID;

public interface DemandStore {
    DemandSummary create(UUID tenantId, UUID actorId, CreateDemandCommand command);

    List<DemandSummary> findAll(UUID tenantId, String status);

    DemandSummary decide(UUID tenantId, UUID demandId, UUID actorId, String status, UUID ownerUserId, String note);
}
