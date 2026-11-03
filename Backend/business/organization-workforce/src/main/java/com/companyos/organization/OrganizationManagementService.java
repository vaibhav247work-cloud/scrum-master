package com.companyos.organization;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.security.contract.AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class OrganizationManagementService {
    private static final Set<String> UNIT_TYPES = Set.of("BUSINESS_UNIT", "DEPARTMENT");
    private static final Set<String> MEMBER_ROLES = Set.of("MEMBER", "LEAD", "BACKUP_LEAD");

    private final OrganizationStore store;
    private final FeatureEntitlementService features;
    private final AuthorizationService authorization;

    public OrganizationManagementService(
            OrganizationStore store,
            FeatureEntitlementService features,
            AuthorizationService authorization) {
        this.store = store;
        this.features = features;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public List<OrganizationUnitSummary> units(UUID tenantId) {
        requireFeatureAndPermission(tenantId, "organization.read");
        return store.findUnits(tenantId);
    }

    @Transactional(readOnly = true)
    public List<TeamSummary> teams(UUID tenantId) {
        requireFeatureAndPermission(tenantId, "team.read");
        return store.findTeams(tenantId);
    }

    @Transactional(readOnly = true)
    public List<TeamMemberSummary> members(UUID tenantId, UUID teamId) {
        requireFeatureAndPermission(tenantId, "team.read");
        return store.findMembers(tenantId, teamId);
    }

    @Transactional
    public OrganizationUnitSummary createUnit(UUID tenantId, UUID actorId, CreateOrganizationUnitCommand command) {
        requireFeatureAndPermission(tenantId, "organization.manage");
        String unitType = required(command.unitType(), "unitType", 32).toUpperCase(Locale.ROOT);
        if (!UNIT_TYPES.contains(unitType)) throw new IllegalArgumentException("unitType must be BUSINESS_UNIT or DEPARTMENT");
        return store.createUnit(tenantId, actorId, new CreateOrganizationUnitCommand(
                command.parentUnitId(), key(command.unitKey(), "unitKey"), required(command.name(), "name", 200),
                unitType, command.managerUserId()), Instant.now());
    }

    @Transactional
    public TeamSummary createTeam(UUID tenantId, UUID actorId, CreateTeamCommand command) {
        requireFeatureAndPermission(tenantId, "team.manage");
        return store.createTeam(tenantId, actorId, new CreateTeamCommand(
                command.parentUnitId(), key(command.teamKey(), "teamKey"), required(command.name(), "name", 200),
                command.managerUserId()), Instant.now());
    }

    @Transactional
    public TeamMemberSummary addMember(UUID tenantId, UUID actorId, UUID teamId, UUID userId, String membershipRole) {
        requireFeatureAndPermission(tenantId, "team.manage");
        String role = required(membershipRole, "membershipRole", 32).toUpperCase(Locale.ROOT);
        if (!MEMBER_ROLES.contains(role)) throw new IllegalArgumentException("membershipRole is invalid");
        return store.addMember(tenantId, actorId, teamId, userId, role, Instant.now());
    }

    @Transactional
    public void removeMember(UUID tenantId, UUID actorId, UUID teamId, UUID userId) {
        requireFeatureAndPermission(tenantId, "team.manage");
        store.removeMember(tenantId, actorId, teamId, userId, Instant.now());
    }

    private void requireFeatureAndPermission(UUID tenantId, String permission) {
        features.requireEnabled(tenantId, "ORGANIZATION_WORKFORCE");
        authorization.requirePermission(permission);
    }

    private static String key(String value, String field) {
        String normalized = required(value, field, 80).toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9][A-Z0-9_-]{0,78}[A-Z0-9]")) {
            throw new IllegalArgumentException(field + " must contain letters, numbers, hyphens, or underscores");
        }
        return normalized;
    }

    private static String required(String value, String field, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        if (normalized.length() > maxLength) throw new IllegalArgumentException(field + " is too long");
        return normalized;
    }
}
