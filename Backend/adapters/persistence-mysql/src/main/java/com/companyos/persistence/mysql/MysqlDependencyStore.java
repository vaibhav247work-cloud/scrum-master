package com.companyos.persistence.mysql;

import com.companyos.dependency.CreateDependencyCommand;
import com.companyos.dependency.DependencyStore;
import com.companyos.dependency.DependencySummary;
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
public class MysqlDependencyStore implements DependencyStore {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MysqlDependencyStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public DependencySummary create(UUID tenantId, UUID actorId, UUID blockedWorkItemId, CreateDependencyCommand command) {
        UUID dependencyId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO work_item_dependency
                    (id, tenant_id, blocked_work_item_id, depends_on_work_item_id,
                     dependency_type, status, created_by, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'OPEN', ?, ?, ?)
                """, uuidBytes(dependencyId), uuidBytes(tenantId), uuidBytes(blockedWorkItemId),
                uuidBytes(command.dependsOnWorkItemId()), command.dependencyType(), uuidBytes(actorId),
                Timestamp.from(now), Timestamp.from(now));
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("dependencyId", dependencyId.toString());
            payload.put("blockedWorkItemId", blockedWorkItemId.toString());
            payload.put("dependsOnWorkItemId", command.dependsOnWorkItemId().toString());
            payload.put("dependencyType", command.dependencyType());
            jdbcTemplate.update("""
                    INSERT INTO outbox_event
                        (event_id, tenant_id, event_type, event_version, aggregate_type, aggregate_id,
                         actor_id, correlation_id, causation_id, occurred_at, payload_json, created_at)
                    VALUES (?, ?, 'DEPENDENCY_CREATED', 1, 'WORK_ITEM_DEPENDENCY', ?, ?, NULL, NULL, ?, ?, ?)
                    """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), uuidBytes(dependencyId), uuidBytes(actorId),
                    Timestamp.from(now), objectMapper.writeValueAsString(payload), Timestamp.from(now));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize dependency event", exception);
        }
        return new DependencySummary(dependencyId, blockedWorkItemId, command.dependsOnWorkItemId(),
                command.dependencyType(), "OPEN", now);
    }

    @Override
    public List<DependencySummary> findForWorkItem(UUID tenantId, UUID workItemId) {
        return jdbcTemplate.query("""
                SELECT id, blocked_work_item_id, depends_on_work_item_id, dependency_type, status, created_at
                FROM work_item_dependency
                WHERE tenant_id = ? AND (blocked_work_item_id = ? OR depends_on_work_item_id = ?)
                ORDER BY created_at DESC, id DESC
                LIMIT 100
                """, this::map, uuidBytes(tenantId), uuidBytes(workItemId), uuidBytes(workItemId));
    }

    private DependencySummary map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new DependencySummary(uuid(resultSet.getBytes("id")), uuid(resultSet.getBytes("blocked_work_item_id")),
                uuid(resultSet.getBytes("depends_on_work_item_id")), resultSet.getString("dependency_type"),
                resultSet.getString("status"), resultSet.getTimestamp("created_at").toInstant());
    }

    private static byte[] uuidBytes(UUID value) { ByteBuffer b = ByteBuffer.allocate(16); b.putLong(value.getMostSignificantBits()); b.putLong(value.getLeastSignificantBits()); return b.array(); }
    private static UUID uuid(byte[] bytes) { ByteBuffer b = ByteBuffer.wrap(bytes); return new UUID(b.getLong(), b.getLong()); }
}
