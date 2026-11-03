package com.companyos.persistence.mysql;

import com.companyos.project.CreateProjectCommand;
import com.companyos.project.ProjectStore;
import com.companyos.project.ProjectSummary;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class MysqlProjectStore implements ProjectStore {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MysqlProjectStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public ProjectSummary create(UUID tenantId, UUID actorId, CreateProjectCommand command) {
        UUID projectId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update("""
            INSERT INTO project
                    (id, tenant_id, product_id, project_key, name, description, status, workflow_id, created_by, created_at, updated_at)
                SELECT ?, ?, ?, ?, ?, ?, 'ACTIVE', workflow.id, ?, ?, ?
                FROM workflow_definition workflow
                WHERE workflow.tenant_id = ? AND workflow.workflow_key = 'DEFAULT' AND workflow.status = 'ACTIVE'
                LIMIT 1
                """, uuidBytes(projectId), uuidBytes(tenantId), nullableUuidBytes(command.productId()), command.projectKey(), command.name(),
                command.description(), uuidBytes(actorId), Timestamp.from(now), Timestamp.from(now), uuidBytes(tenantId));
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "projectId", projectId.toString(),
                    "projectKey", command.projectKey(),
                    "name", command.name()));
            jdbcTemplate.update("""
                    INSERT INTO outbox_event
                        (event_id, tenant_id, event_type, event_version, aggregate_type, aggregate_id,
                         actor_id, correlation_id, causation_id, occurred_at, payload_json, created_at)
                    VALUES (?, ?, 'PROJECT_CREATED', 1, 'PROJECT', ?, ?, NULL, NULL, ?, ?, ?)
                    """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), uuidBytes(projectId), uuidBytes(actorId),
                    Timestamp.from(now), payload, Timestamp.from(now));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize project event", exception);
        }
        return new ProjectSummary(projectId, command.projectKey(), command.name(), command.description(), "ACTIVE", now, command.productId());
    }

    @Override
    public List<ProjectSummary> findAll(UUID tenantId) {
        return jdbcTemplate.query("""
                SELECT id, product_id, project_key, name, description, status, created_at
                FROM project
                WHERE tenant_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT 100
                """, this::map, uuidBytes(tenantId));
    }

    private ProjectSummary map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ProjectSummary(uuid(resultSet.getBytes("id")), resultSet.getString("project_key"),
                resultSet.getString("name"), resultSet.getString("description"), resultSet.getString("status"),
                resultSet.getTimestamp("created_at").toInstant(), nullableUuid(resultSet.getBytes("product_id")));
    }

    private static byte[] uuidBytes(UUID value) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(value.getMostSignificantBits());
        buffer.putLong(value.getLeastSignificantBits());
        return buffer.array();
    }

    private static byte[] nullableUuidBytes(UUID value) { return value == null ? null : uuidBytes(value); }

    private static UUID uuid(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    private static UUID nullableUuid(byte[] bytes) { return bytes == null ? null : uuid(bytes); }
}
