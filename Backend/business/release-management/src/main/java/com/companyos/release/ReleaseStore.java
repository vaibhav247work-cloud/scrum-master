package com.companyos.release;

import java.util.List;
import java.util.UUID;

public interface ReleaseStore {
    ReleaseSummary create(UUID tenantId, UUID projectId, UUID actorId, CreateReleaseCommand command);
    List<ReleaseSummary> findByProject(UUID tenantId, UUID projectId);
    void addWorkItem(UUID tenantId, UUID actorId, UUID releaseId, UUID workItemId);
}
