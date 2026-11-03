package com.companyos.resource;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ResourceWorkloadStore {
    List<ResourceWorkloadMember> findWorkload(UUID tenantId, LocalDate weekStart, int recentActivityDays);

    List<ResourceAllocationSummary> findAllocations(UUID tenantId, LocalDate weekStart, LocalDate weekEnd);

    ResourceAllocationSummary createAllocation(UUID tenantId, UUID actorId, CreateResourceAllocationCommand command);
}
