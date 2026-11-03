package com.companyos.project;

import java.util.List;
import java.util.UUID;

public interface ProjectTeamStore {
    List<ProjectTeamSummary> findTeams(UUID tenantId, UUID projectId);

    ProjectTeamSummary addTeam(UUID tenantId, UUID projectId, UUID teamId, UUID actorId, String relationshipRole);

    void removeTeam(UUID tenantId, UUID projectId, UUID teamId, UUID actorId);
}
