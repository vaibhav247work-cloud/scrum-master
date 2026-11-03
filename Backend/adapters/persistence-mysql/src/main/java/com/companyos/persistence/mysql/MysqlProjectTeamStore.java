package com.companyos.persistence.mysql;

import com.companyos.project.ProjectTeamStore;
import com.companyos.project.ProjectTeamSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;

@Repository
public class MysqlProjectTeamStore implements ProjectTeamStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public MysqlProjectTeamStore(JdbcTemplate jdbc, ObjectMapper objectMapper) { this.jdbc = jdbc; this.objectMapper = objectMapper; }

    @Override
    public List<ProjectTeamSummary> findTeams(UUID tenantId, UUID projectId) {
        return jdbc.query("""
                SELECT project_id, team_id, relationship_role, created_at
                FROM project_team WHERE tenant_id = ? AND project_id = ?
                ORDER BY created_at, team_id
                """, this::map, bytes(tenantId), bytes(projectId));
    }

    @Override
    public ProjectTeamSummary addTeam(UUID tenantId, UUID projectId, UUID teamId, UUID actorId, String relationshipRole) {
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO project_team (tenant_id, project_id, team_id, relationship_role, created_by, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE relationship_role = VALUES(relationship_role)
                """, bytes(tenantId), bytes(projectId), bytes(teamId), relationshipRole, bytes(actorId), Timestamp.from(now));
        recordMutation(tenantId, actorId, "PROJECT_TEAM_LINKED", projectId,
                Map.of("teamId", teamId.toString(), "relationshipRole", relationshipRole), now);
        return new ProjectTeamSummary(projectId, teamId, relationshipRole, now);
    }

    @Override
    public void removeTeam(UUID tenantId, UUID projectId, UUID teamId, UUID actorId) {
        int changed = jdbc.update("DELETE FROM project_team WHERE tenant_id = ? AND project_id = ? AND team_id = ?",
                bytes(tenantId), bytes(projectId), bytes(teamId));
        if (changed == 1) recordMutation(tenantId, actorId, "PROJECT_TEAM_UNLINKED", projectId,
                Map.of("teamId", teamId.toString()), Instant.now());
    }

    private void recordMutation(UUID tenantId, UUID actorId, String eventType, UUID aggregateId,
                                Map<String, Object> values, Instant now) {
        if (actorId == null) return;
        try {
            String payload = objectMapper.writeValueAsString(new LinkedHashMap<>(values));
            jdbc.update("""
                    INSERT INTO audit_event (id, tenant_id, actor_id, event_type, aggregate_type, aggregate_id,
                                             correlation_id, occurred_at, payload_json)
                    VALUES (?, ?, ?, ?, 'PROJECT', ?, NULL, ?, CAST(? AS JSON))
                    """, bytes(UUID.randomUUID()), bytes(tenantId), bytes(actorId), eventType, bytes(aggregateId), Timestamp.from(now), payload);
            jdbc.update("""
                    INSERT INTO outbox_event (event_id, tenant_id, event_type, event_version, aggregate_type, aggregate_id,
                                              actor_id, correlation_id, causation_id, occurred_at, payload_json, created_at)
                    VALUES (?, ?, ?, 1, 'PROJECT', ?, ?, NULL, NULL, ?, CAST(? AS JSON), ?)
                    """, bytes(UUID.randomUUID()), bytes(tenantId), eventType, bytes(aggregateId), bytes(actorId), Timestamp.from(now), payload, Timestamp.from(now));
        } catch (JsonProcessingException exception) { throw new IllegalStateException("Unable to serialize project team event", exception); }
    }

    private ProjectTeamSummary map(ResultSet rs, int row) throws SQLException {
        return new ProjectTeamSummary(uuid(rs.getBytes("project_id")), uuid(rs.getBytes("team_id")),
                rs.getString("relationship_role"), rs.getTimestamp("created_at").toInstant());
    }
    private static byte[] bytes(UUID value) { return uuidBytes(value); }
    private static byte[] uuidBytes(UUID value) { ByteBuffer b = ByteBuffer.allocate(16); b.putLong(value.getMostSignificantBits()); b.putLong(value.getLeastSignificantBits()); return b.array(); }
    private static UUID uuid(byte[] value) { ByteBuffer b = ByteBuffer.wrap(value); return new UUID(b.getLong(), b.getLong()); }
}
