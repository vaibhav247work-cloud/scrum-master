package com.companyos.persistence.mysql;

import com.companyos.release.CreateReleaseCommand;
import com.companyos.release.ReleaseStore;
import com.companyos.release.ReleaseSummary;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class MysqlReleaseStore implements ReleaseStore {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MysqlReleaseStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public ReleaseSummary create(UUID tenantId, UUID projectId, UUID actorId, CreateReleaseCommand command) {
        UUID releaseId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO release_item
                    (id, tenant_id, project_id, release_key, name, description, target_date,
                     status, created_by, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'PLANNED', ?, ?, ?)
                """, uuidBytes(releaseId), uuidBytes(tenantId), uuidBytes(projectId), command.releaseKey(),
                command.name(), command.description(), command.targetDate() == null ? null : Date.valueOf(command.targetDate()),
                uuidBytes(actorId), Timestamp.from(now), Timestamp.from(now));
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("releaseId", releaseId.toString());
            payload.put("projectId", projectId.toString());
            payload.put("releaseKey", command.releaseKey());
            payload.put("name", command.name());
            String eventPayload = objectMapper.writeValueAsString(payload);
            jdbcTemplate.update("""
                    INSERT INTO outbox_event
                        (event_id, tenant_id, event_type, event_version, aggregate_type, aggregate_id,
                         actor_id, correlation_id, causation_id, occurred_at, payload_json, created_at)
                    VALUES (?, ?, 'RELEASE_CREATED', 1, 'RELEASE', ?, ?, NULL, NULL, ?, ?, ?)
                    """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), uuidBytes(releaseId), uuidBytes(actorId),
                    Timestamp.from(now), eventPayload, Timestamp.from(now));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize release event", exception);
        }
        return new ReleaseSummary(releaseId, projectId, command.releaseKey(), command.name(), command.description(),
                command.targetDate(), "PLANNED", now);
    }

    @Override
    public List<ReleaseSummary> findByProject(UUID tenantId, UUID projectId) {
        return jdbcTemplate.query("""
                SELECT id, project_id, release_key, name, description, target_date, status, created_at
                FROM release_item
                WHERE tenant_id = ? AND project_id = ?
                ORDER BY COALESCE(target_date, '9999-12-31'), created_at DESC, id DESC
                LIMIT 100
                """, this::map, uuidBytes(tenantId), uuidBytes(projectId));
    }

    @Override
    public void addWorkItem(UUID tenantId, UUID actorId, UUID releaseId, UUID workItemId) {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO release_work_item (tenant_id, release_id, work_item_id, added_by, added_at)
                VALUES (?, ?, ?, ?, ?)
                """, uuidBytes(tenantId), uuidBytes(releaseId), uuidBytes(workItemId), uuidBytes(actorId), Timestamp.from(now));
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("releaseId", releaseId.toString());
            payload.put("workItemId", workItemId.toString());
            jdbcTemplate.update("""
                    INSERT INTO outbox_event
                        (event_id, tenant_id, event_type, event_version, aggregate_type, aggregate_id,
                         actor_id, correlation_id, causation_id, occurred_at, payload_json, created_at)
                    VALUES (?, ?, 'RELEASE_WORK_ITEM_ADDED', 1, 'RELEASE', ?, ?, NULL, NULL, ?, ?, ?)
                    """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), uuidBytes(releaseId), uuidBytes(actorId),
                    Timestamp.from(now), objectMapper.writeValueAsString(payload), Timestamp.from(now));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize release work item event", exception);
        }
    }

    private ReleaseSummary map(ResultSet resultSet, int rowNumber) throws SQLException {
        Date targetDate = resultSet.getDate("target_date");
        return new ReleaseSummary(uuid(resultSet.getBytes("id")), uuid(resultSet.getBytes("project_id")),
                resultSet.getString("release_key"), resultSet.getString("name"), resultSet.getString("description"),
                targetDate == null ? null : targetDate.toLocalDate(), resultSet.getString("status"),
                resultSet.getTimestamp("created_at").toInstant());
    }

    private static byte[] uuidBytes(UUID value) { ByteBuffer b = ByteBuffer.allocate(16); b.putLong(value.getMostSignificantBits()); b.putLong(value.getLeastSignificantBits()); return b.array(); }
    private static UUID uuid(byte[] bytes) { ByteBuffer b = ByteBuffer.wrap(bytes); return new UUID(b.getLong(), b.getLong()); }
}
