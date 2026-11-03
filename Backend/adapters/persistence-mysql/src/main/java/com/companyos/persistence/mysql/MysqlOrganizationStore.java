package com.companyos.persistence.mysql;

import com.companyos.organization.CreateOrganizationUnitCommand;
import com.companyos.organization.CreateTeamCommand;
import com.companyos.organization.OrganizationStore;
import com.companyos.organization.OrganizationUnitSummary;
import com.companyos.organization.TeamMemberSummary;
import com.companyos.organization.TeamSummary;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class MysqlOrganizationStore implements OrganizationStore {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MysqlOrganizationStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<OrganizationUnitSummary> findUnits(UUID tenantId) {
        return jdbcTemplate.query("""
                SELECT id, parent_unit_id, unit_key, name, unit_type, manager_user_id, status, created_at
                FROM organization_unit
                WHERE tenant_id = ? AND status = 'ACTIVE'
                ORDER BY unit_type, name, id
                """, this::mapUnit, uuidBytes(tenantId));
    }

    @Override
    public List<TeamSummary> findTeams(UUID tenantId) {
        return jdbcTemplate.query("""
                SELECT t.id, t.parent_unit_id, t.team_key, t.name, t.manager_user_id, t.status,
                       COUNT(tm.user_id) AS member_count, t.created_at
                FROM team t
                LEFT JOIN team_membership tm
                    ON tm.tenant_id = t.tenant_id AND tm.team_id = t.id AND tm.status = 'ACTIVE'
                WHERE t.tenant_id = ? AND t.status = 'ACTIVE'
                GROUP BY t.id, t.parent_unit_id, t.team_key, t.name, t.manager_user_id, t.status, t.created_at
                ORDER BY t.name, t.id
                """, this::mapTeam, uuidBytes(tenantId));
    }

    @Override
    public List<TeamMemberSummary> findMembers(UUID tenantId, UUID teamId) {
        return jdbcTemplate.query("""
                SELECT u.id, u.email, u.display_name, tm.membership_role, tm.status,
                       tm.joined_at, tm.left_at
                FROM team_membership tm
                JOIN tenant_user u ON u.tenant_id = tm.tenant_id AND u.id = tm.user_id
                WHERE tm.tenant_id = ? AND tm.team_id = ? AND tm.status = 'ACTIVE'
                ORDER BY tm.membership_role, u.display_name, u.id
                """, this::mapMember, uuidBytes(tenantId), uuidBytes(teamId));
    }

    @Override
    public OrganizationUnitSummary createUnit(UUID tenantId, UUID actorId,
                                               CreateOrganizationUnitCommand command, Instant now) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO organization_unit
                    (id, tenant_id, parent_unit_id, unit_key, name, unit_type, manager_user_id,
                     status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
                """, uuidBytes(id), uuidBytes(tenantId), uuidBytes(command.parentUnitId()), command.unitKey(),
                command.name(), command.unitType(), uuidBytes(command.managerUserId()),
                Timestamp.from(now), Timestamp.from(now));
        recordMutation(tenantId, actorId, "ORGANIZATION_UNIT_CREATED", "ORGANIZATION_UNIT", id,
                Map.of("unitKey", command.unitKey(), "unitType", command.unitType()), now);
        return new OrganizationUnitSummary(id, command.parentUnitId(), command.unitKey(), command.name(),
                command.unitType(), command.managerUserId(), "ACTIVE", now);
    }

    @Override
    public TeamSummary createTeam(UUID tenantId, UUID actorId, CreateTeamCommand command, Instant now) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO team
                    (id, tenant_id, parent_unit_id, team_key, name, manager_user_id,
                     status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
                """, uuidBytes(id), uuidBytes(tenantId), uuidBytes(command.parentUnitId()), command.teamKey(),
                command.name(), uuidBytes(command.managerUserId()), Timestamp.from(now), Timestamp.from(now));
        recordMutation(tenantId, actorId, "TEAM_CREATED", "TEAM", id,
                Map.of("teamKey", command.teamKey()), now);
        return new TeamSummary(id, command.parentUnitId(), command.teamKey(), command.name(),
                command.managerUserId(), "ACTIVE", 0, now);
    }

    @Override
    public TeamMemberSummary addMember(UUID tenantId, UUID actorId, UUID teamId, UUID userId,
                                       String membershipRole, Instant now) {
        int inserted = jdbcTemplate.update("""
                INSERT INTO team_membership
                    (tenant_id, team_id, user_id, membership_role, status, joined_at, left_at, created_by)
                SELECT ?, ?, u.id, ?, 'ACTIVE', ?, NULL, ?
                FROM tenant_user u
                WHERE u.tenant_id = ? AND u.id = ? AND u.status IN ('ACTIVE', 'INVITED')
                  AND EXISTS (SELECT 1 FROM team t WHERE t.tenant_id = ? AND t.id = ? AND t.status = 'ACTIVE')
                """, uuidBytes(tenantId), uuidBytes(teamId), membershipRole, Timestamp.from(now), uuidBytes(actorId),
                uuidBytes(tenantId), uuidBytes(userId), uuidBytes(tenantId), uuidBytes(teamId));
        if (inserted != 1) throw new IllegalArgumentException("Active tenant user or team was not found");
        recordMutation(tenantId, actorId, "TEAM_MEMBER_ADDED", "TEAM", teamId,
                Map.of("userId", userId.toString(), "membershipRole", membershipRole), now);
        return jdbcTemplate.queryForObject("""
                SELECT u.id, u.email, u.display_name, tm.membership_role, tm.status,
                       tm.joined_at, tm.left_at
                FROM team_membership tm
                JOIN tenant_user u ON u.tenant_id = tm.tenant_id AND u.id = tm.user_id
                WHERE tm.tenant_id = ? AND tm.team_id = ? AND tm.user_id = ?
                """, this::mapMember, uuidBytes(tenantId), uuidBytes(teamId), uuidBytes(userId));
    }

    @Override
    public void removeMember(UUID tenantId, UUID actorId, UUID teamId, UUID userId, Instant now) {
        int updated = jdbcTemplate.update("""
                UPDATE team_membership
                SET status = 'INACTIVE', left_at = ?
                WHERE tenant_id = ? AND team_id = ? AND user_id = ? AND status = 'ACTIVE'
                """, Timestamp.from(now), uuidBytes(tenantId), uuidBytes(teamId), uuidBytes(userId));
        if (updated != 1) throw new IllegalArgumentException("Active team membership was not found");
        recordMutation(tenantId, actorId, "TEAM_MEMBER_REMOVED", "TEAM", teamId,
                Map.of("userId", userId.toString()), now);
    }

    private void recordMutation(UUID tenantId, UUID actorId, String eventType, String aggregateType,
                                UUID aggregateId, Map<String, Object> values, Instant now) {
        try {
            String payload = objectMapper.writeValueAsString(new LinkedHashMap<>(values));
            jdbcTemplate.update("""
                    INSERT INTO audit_event
                        (id, tenant_id, actor_id, event_type, aggregate_type, aggregate_id,
                         correlation_id, occurred_at, payload_json)
                    VALUES (?, ?, ?, ?, ?, ?, NULL, ?, CAST(? AS JSON))
                    """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), uuidBytes(actorId), eventType,
                    aggregateType, uuidBytes(aggregateId), Timestamp.from(now), payload);
            jdbcTemplate.update("""
                    INSERT INTO outbox_event
                        (event_id, tenant_id, event_type, event_version, aggregate_type, aggregate_id,
                         actor_id, correlation_id, causation_id, occurred_at, payload_json, created_at)
                    VALUES (?, ?, ?, 1, ?, ?, ?, NULL, NULL, ?, CAST(? AS JSON), ?)
                    """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), eventType, aggregateType,
                    uuidBytes(aggregateId), uuidBytes(actorId), Timestamp.from(now), payload, Timestamp.from(now));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize organization event", exception);
        }
    }

    private OrganizationUnitSummary mapUnit(ResultSet resultSet, int rowNumber) throws SQLException {
        return new OrganizationUnitSummary(uuid(resultSet.getBytes("id")), nullableUuid(resultSet.getBytes("parent_unit_id")),
                resultSet.getString("unit_key"), resultSet.getString("name"), resultSet.getString("unit_type"),
                nullableUuid(resultSet.getBytes("manager_user_id")), resultSet.getString("status"),
                resultSet.getTimestamp("created_at").toInstant());
    }

    private TeamSummary mapTeam(ResultSet resultSet, int rowNumber) throws SQLException {
        return new TeamSummary(uuid(resultSet.getBytes("id")), nullableUuid(resultSet.getBytes("parent_unit_id")),
                resultSet.getString("team_key"), resultSet.getString("name"),
                nullableUuid(resultSet.getBytes("manager_user_id")), resultSet.getString("status"),
                resultSet.getInt("member_count"), resultSet.getTimestamp("created_at").toInstant());
    }

    private TeamMemberSummary mapMember(ResultSet resultSet, int rowNumber) throws SQLException {
        Timestamp leftAt = resultSet.getTimestamp("left_at");
        return new TeamMemberSummary(uuid(resultSet.getBytes("id")), resultSet.getString("email"),
                resultSet.getString("display_name"), resultSet.getString("membership_role"),
                resultSet.getString("status"), resultSet.getTimestamp("joined_at").toInstant(),
                leftAt == null ? null : leftAt.toInstant());
    }

    private static byte[] uuidBytes(UUID value) {
        if (value == null) return null;
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(value.getMostSignificantBits());
        buffer.putLong(value.getLeastSignificantBits());
        return buffer.array();
    }

    private static UUID uuid(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    private static UUID nullableUuid(byte[] bytes) {
        return bytes == null ? null : uuid(bytes);
    }
}
