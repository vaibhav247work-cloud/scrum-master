package com.companyos.project;

import java.util.List;
import java.util.UUID;

public interface ProjectStore {
    ProjectSummary create(UUID tenantId, UUID actorId, CreateProjectCommand command);

    List<ProjectSummary> findAll(UUID tenantId);
}
