package com.companyos.persistence.mysql;

import com.companyos.demand.CreateDemandCommand;
import com.companyos.demand.DemandStore;
import com.companyos.demand.DemandSummary;
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
public class MysqlDemandStore implements DemandStore {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MysqlDemandStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public DemandSummary create(UUID tenantId, UUID actorId, CreateDemandCommand command) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO demand_item
                    (id, tenant_id, demand_key, title, description, demand_type, priority, status,
                     requested_by, decision_note, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'SUBMITTED', ?, '', ?, ?)
                """, uuidBytes(id), uuidBytes(tenantId), command.demandKey(), command.title(), command.description(),
                command.demandType(), command.priority(), uuidBytes(actorId), Timestamp.from(now), Timestamp.from(now));
        publish(tenantId, actorId, id, "DEMAND_CREATED", now, Map.of(
                "demandId", id.toString(), "demandKey", command.demandKey(), "demandType", command.demandType(),
                "priority", command.priority()));
        return new DemandSummary(id, command.demandKey(), command.title(), command.description(), command.demandType(),
                command.priority(), "SUBMITTED", actorId, null, null, "", null, now, now);
    }

    @Override
    public List<DemandSummary> findAll(UUID tenantId, String status) {
        if (status == null) {
            return jdbcTemplate.query(baseQuery() + " WHERE tenant_id = ? ORDER BY created_at DESC, id DESC LIMIT 500",
                    this::map, uuidBytes(tenantId));
        }
        return jdbcTemplate.query(baseQuery() + " WHERE tenant_id = ? AND status = ? ORDER BY created_at DESC, id DESC LIMIT 500",
                this::map, uuidBytes(tenantId), status);
    }

    @Override
    public DemandSummary decide(UUID tenantId, UUID demandId, UUID actorId, String status,
                                UUID ownerUserId, String note) {
        Instant now = Instant.now();
        int updated = jdbcTemplate.update("""
                UPDATE demand_item
                SET status = ?, owner_user_id = COALESCE(?, owner_user_id),
                    decided_by = ?, decision_note = ?, decided_at = ?, updated_at = ?
                WHERE tenant_id = ? AND id = ? AND status IN ('SUBMITTED', 'IN_REVIEW', 'DEFERRED')
                """, status, uuidBytes(ownerUserId), uuidBytes(actorId), note, Timestamp.from(now), Timestamp.from(now),
                uuidBytes(tenantId), uuidBytes(demandId));
        if (updated == 0) throw new IllegalArgumentException("demand was not found or cannot be decided from its current status");
        publish(tenantId, actorId, demandId, "DEMAND_STATUS_CHANGED", now, Map.of(
                "demandId", demandId.toString(), "status", status, "decisionNote", note));
        return jdbcTemplate.queryForObject(baseQuery() + " WHERE tenant_id = ? AND id = ?",
                this::map, uuidBytes(tenantId), uuidBytes(demandId));
    }

    private String baseQuery() {
        return """
                SELECT id, demand_key, title, description, demand_type, priority, status,
                       requested_by, owner_user_id, decided_by, decision_note, decided_at, created_at, updated_at
                FROM demand_item
                """;
    }

    private DemandSummary map(ResultSet rs, int row) throws SQLException {
        Timestamp decidedAt = rs.getTimestamp("decided_at");
        return new DemandSummary(uuid(rs.getBytes("id")), rs.getString("demand_key"), rs.getString("title"),
                rs.getString("description"), rs.getString("demand_type"), rs.getString("priority"), rs.getString("status"),
                uuid(rs.getBytes("requested_by")), nullableUuid(rs.getBytes("owner_user_id")), nullableUuid(rs.getBytes("decided_by")),
                rs.getString("decision_note"), decidedAt == null ? null : decidedAt.toInstant(),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private void publish(UUID tenantId, UUID actorId, UUID aggregateId, String type, Instant now, Map<String, Object> values) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO outbox_event
                        (event_id, tenant_id, event_type, event_version, aggregate_type, aggregate_id,
                         actor_id, correlation_id, causation_id, occurred_at, payload_json, created_at)
                    VALUES (?, ?, ?, 1, 'DEMAND', ?, ?, NULL, NULL, ?, ?, ?)
                    """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), type, uuidBytes(aggregateId), uuidBytes(actorId),
                    Timestamp.from(now), objectMapper.writeValueAsString(values), Timestamp.from(now));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize demand event", exception);
        }
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

    private static UUID nullableUuid(byte[] bytes) { return bytes == null ? null : uuid(bytes); }
}
