package com.companyos.organization;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OrganizationStore {
    List<OrganizationUnitSummary> findUnits(UUID tenantId);

    List<TeamSummary> findTeams(UUID tenantId);

    List<TeamMemberSummary> findMembers(UUID tenantId, UUID teamId);

    OrganizationUnitSummary createUnit(UUID tenantId, UUID actorId, CreateOrganizationUnitCommand command, Instant now);

    TeamSummary createTeam(UUID tenantId, UUID actorId, CreateTeamCommand command, Instant now);

    TeamMemberSummary addMember(UUID tenantId, UUID actorId, UUID teamId, UUID userId, String membershipRole, Instant now);

    void removeMember(UUID tenantId, UUID actorId, UUID teamId, UUID userId, Instant now);
}
