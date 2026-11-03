package com.companyos.dependency;

import java.util.List;
import java.util.UUID;

public interface DependencyStore {
    DependencySummary create(UUID tenantId, UUID actorId, UUID blockedWorkItemId, CreateDependencyCommand command);
    List<DependencySummary> findForWorkItem(UUID tenantId, UUID workItemId);
}
