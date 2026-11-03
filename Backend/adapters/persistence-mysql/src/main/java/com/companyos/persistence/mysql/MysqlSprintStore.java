package com.companyos.persistence.mysql;

import com.companyos.sprint.CreateSprintCommand;
import com.companyos.sprint.SprintStore;
import com.companyos.sprint.SprintSummary;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.ArrayList;
import java.util.StringJoiner;

@Repository
public class MysqlSprintStore implements SprintStore {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MysqlSprintStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public SprintSummary create(UUID tenantId, UUID projectId, UUID actorId, CreateSprintCommand command) {
        UUID sprintId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO sprint
                    (id, tenant_id, project_id, sprint_key, name, goal, start_date, end_date, status,
                     created_by, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PLANNED', ?, ?, ?)
                """, uuidBytes(sprintId), uuidBytes(tenantId), uuidBytes(projectId), command.sprintKey(),
                command.name(), command.goal(), date(command.startDate()), date(command.endDate()), uuidBytes(actorId),
                Timestamp.from(now), Timestamp.from(now));
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "sprintId", sprintId.toString(),
                    "projectId", projectId.toString(),
                    "sprintKey", command.sprintKey(),
                    "name", command.name()));
            jdbcTemplate.update("""
                    INSERT INTO outbox_event
                        (event_id, tenant_id, event_type, event_version, aggregate_type, aggregate_id,
                         actor_id, correlation_id, causation_id, occurred_at, payload_json, created_at)
                    VALUES (?, ?, 'SPRINT_CREATED', 1, 'SPRINT', ?, ?, NULL, NULL, ?, ?, ?)
                    """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), uuidBytes(sprintId), uuidBytes(actorId),
                    Timestamp.from(now), payload, Timestamp.from(now));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize sprint event", exception);
        }
        return new SprintSummary(sprintId, projectId, command.sprintKey(), command.name(), command.goal(),
                command.startDate(), command.endDate(), "PLANNED", now);
    }

    @Override
    public List<SprintSummary> findByProject(UUID tenantId, UUID projectId) {
        return jdbcTemplate.query("""
                SELECT id, project_id, sprint_key, name, goal, start_date, end_date, status, created_at
                FROM sprint
                WHERE tenant_id = ? AND project_id = ?
                ORDER BY COALESCE(start_date, '9999-12-31'), created_at DESC, id DESC
                LIMIT 100
                """, this::map, uuidBytes(tenantId), uuidBytes(projectId));
    }

    @Override
    public Optional<SprintSummary> findById(UUID tenantId, UUID sprintId) {
        return jdbcTemplate.query("""
                SELECT id, project_id, sprint_key, name, goal, start_date, end_date, status, created_at
                FROM sprint WHERE tenant_id = ? AND id = ?
                """, this::map, uuidBytes(tenantId), uuidBytes(sprintId)).stream().findFirst();
    }

    @Override
    public SprintSummary updateStatus(UUID tenantId, UUID sprintId, UUID actorId, String status, String completionNote) {
        Instant now = Instant.now();
        int changed = jdbcTemplate.update("""
                UPDATE sprint
                SET status = ?, updated_at = ?, completed_at = ?, completed_by = ?, completion_note = ?
                WHERE tenant_id = ? AND id = ?
                """, status, Timestamp.from(now), "COMPLETED".equals(status) ? Timestamp.from(now) : null,
                "COMPLETED".equals(status) ? uuidBytes(actorId) : null, completionNote,
                uuidBytes(tenantId), uuidBytes(sprintId));
        if (changed != 1) throw new IllegalArgumentException("sprint was not found");
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "sprintId", sprintId.toString(), "status", status, "completionNote", completionNote));
            jdbcTemplate.update("""
                    INSERT INTO outbox_event
                        (event_id, tenant_id, event_type, event_version, aggregate_type, aggregate_id,
                         actor_id, correlation_id, causation_id, occurred_at, payload_json, created_at)
                    VALUES (?, ?, 'SPRINT_STATUS_CHANGED', 1, 'SPRINT', ?, ?, NULL, NULL, ?, ?, ?)
                    """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), uuidBytes(sprintId), uuidBytes(actorId),
                    Timestamp.from(now), payload, Timestamp.from(now));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize sprint status event", exception);
        }
        return findById(tenantId, sprintId).orElseThrow(() -> new IllegalArgumentException("sprint was not found"));
    }

    @Override
    public com.companyos.sprint.SprintProgressSummary progress(UUID tenantId, UUID sprintId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) total_count,
                       COALESCE(SUM(status = 'TODO'), 0) todo_count,
                       COALESCE(SUM(status = 'IN_PROGRESS'), 0) in_progress_count,
                       COALESCE(SUM(status = 'BLOCKED'), 0) blocked_count,
                       COALESCE(SUM(status = 'DONE'), 0) completed_count,
                       COALESCE(SUM(status = 'CANCELLED'), 0) cancelled_count,
                       COALESCE(SUM(status NOT IN ('DONE', 'CANCELLED')), 0) carry_over_count
                FROM work_item WHERE tenant_id = ? AND sprint_id = ?
                """, (rs, row) -> new com.companyos.sprint.SprintProgressSummary(sprintId,
                        rs.getInt("total_count"), rs.getInt("todo_count"), rs.getInt("in_progress_count"),
                        rs.getInt("blocked_count"), rs.getInt("completed_count"), rs.getInt("cancelled_count"),
                        rs.getInt("carry_over_count")), uuidBytes(tenantId), uuidBytes(sprintId));
    }

    @Override
    public com.companyos.sprint.SprintCarryOverResult carryOver(UUID tenantId, UUID sourceSprintId,
                                                                  UUID targetSprintId, UUID actorId,
                                                                  Set<UUID> workItemIds) {
        SprintPair pair = jdbcTemplate.query("""
                SELECT id, project_id, status FROM sprint
                WHERE tenant_id = ? AND id IN (?, ?)
                FOR UPDATE
                """, rs -> {
            SprintPair found = new SprintPair();
            while (rs.next()) {
                UUID id = uuid(rs.getBytes("id"));
                if (id.equals(sourceSprintId)) found.source = new SprintState(uuid(rs.getBytes("project_id")), rs.getString("status"));
                if (id.equals(targetSprintId)) found.target = new SprintState(uuid(rs.getBytes("project_id")), rs.getString("status"));
            }
            return found;
        }, uuidBytes(tenantId), uuidBytes(sourceSprintId), uuidBytes(targetSprintId));
        if (pair.source == null || pair.target == null) throw new IllegalArgumentException("source or target sprint was not found");
        if (!pair.source.projectId().equals(pair.target.projectId())) throw new IllegalArgumentException("source and target sprint must belong to the same project");
        if (!"COMPLETED".equals(pair.source.status())) throw new IllegalArgumentException("only a completed sprint can be carried over");
        if (!Set.of("PLANNED", "ACTIVE").contains(pair.target.status())) throw new IllegalArgumentException("target sprint must be planned or active");

        StringBuilder sql = new StringBuilder("UPDATE work_item SET sprint_id = ?, updated_at = ? WHERE tenant_id = ? AND sprint_id = ? AND status NOT IN ('DONE', 'CANCELLED')");
        List<Object> args = new ArrayList<>();
        args.add(uuidBytes(targetSprintId));
        args.add(Timestamp.from(Instant.now()));
        args.add(uuidBytes(tenantId));
        args.add(uuidBytes(sourceSprintId));
        if (!workItemIds.isEmpty()) {
            StringJoiner placeholders = new StringJoiner(", ");
            for (UUID ignored : workItemIds) placeholders.add("?");
            sql.append(" AND id IN (").append(placeholders).append(")");
            workItemIds.forEach(id -> args.add(uuidBytes(id)));
        }
        int moved = jdbcTemplate.update(sql.toString(), args.toArray());
        int remaining = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM work_item
                WHERE tenant_id = ? AND sprint_id = ? AND status NOT IN ('DONE', 'CANCELLED')
                """, Integer.class, uuidBytes(tenantId), uuidBytes(sourceSprintId));
        Instant now = Instant.now();
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "sourceSprintId", sourceSprintId.toString(),
                    "targetSprintId", targetSprintId.toString(),
                    "movedWorkItemCount", moved,
                    "remainingWorkItemCount", remaining));
            jdbcTemplate.update("""
                    INSERT INTO audit_event (id, tenant_id, actor_id, event_type, aggregate_type, aggregate_id,
                                             correlation_id, occurred_at, payload_json)
                    VALUES (?, ?, ?, 'SPRINT_WORK_CARRIED_OVER', 'SPRINT', ?, NULL, ?, CAST(? AS JSON))
                    """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), uuidBytes(actorId), uuidBytes(sourceSprintId), Timestamp.from(now), payload);
            jdbcTemplate.update("""
                    INSERT INTO outbox_event (event_id, tenant_id, event_type, event_version, aggregate_type, aggregate_id,
                                              actor_id, correlation_id, causation_id, occurred_at, payload_json, created_at)
                    VALUES (?, ?, 'SPRINT_WORK_CARRIED_OVER', 1, 'SPRINT', ?, ?, NULL, NULL, ?, CAST(? AS JSON), ?)
                    """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), uuidBytes(sourceSprintId), uuidBytes(actorId), Timestamp.from(now), payload, Timestamp.from(now));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize sprint carry-over event", exception);
        }
        return new com.companyos.sprint.SprintCarryOverResult(sourceSprintId, targetSprintId, moved, remaining);
    }

    private static final class SprintPair {
        private SprintState source;
        private SprintState target;
    }

    private record SprintState(UUID projectId, String status) { }

    private SprintSummary map(ResultSet resultSet, int rowNumber) throws SQLException {
        Date startDate = resultSet.getDate("start_date");
        Date endDate = resultSet.getDate("end_date");
        return new SprintSummary(uuid(resultSet.getBytes("id")), uuid(resultSet.getBytes("project_id")),
                resultSet.getString("sprint_key"), resultSet.getString("name"), resultSet.getString("goal"),
                startDate == null ? null : startDate.toLocalDate(), endDate == null ? null : endDate.toLocalDate(),
                resultSet.getString("status"), resultSet.getTimestamp("created_at").toInstant());
    }

    private static Date date(java.time.LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }

    private static byte[] uuidBytes(UUID value) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(value.getMostSignificantBits());
        buffer.putLong(value.getLeastSignificantBits());
        return buffer.array();
    }

    private static UUID uuid(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
