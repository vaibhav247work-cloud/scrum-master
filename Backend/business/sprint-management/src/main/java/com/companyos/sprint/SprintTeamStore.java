package com.companyos.sprint;

import java.util.List;
import java.util.UUID;

public interface SprintTeamStore {
    List<SprintTeamSummary> findTeams(UUID tenantId, UUID sprintId);

    SprintTeamSummary addTeam(UUID tenantId, UUID sprintId, UUID teamId, UUID actorId, String relationshipRole);

    void removeTeam(UUID tenantId, UUID sprintId, UUID teamId, UUID actorId);
}
