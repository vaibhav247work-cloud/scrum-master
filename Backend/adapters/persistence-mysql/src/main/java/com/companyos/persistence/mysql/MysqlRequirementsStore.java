package com.companyos.persistence.mysql;

import com.companyos.requirements.CreateRequirementCommand;
import com.companyos.requirements.ReadinessCheck;
import com.companyos.requirements.RequirementSummary;
import com.companyos.requirements.RequirementsStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class MysqlRequirementsStore implements RequirementsStore {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MysqlRequirementsStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public RequirementSummary create(UUID tenantId, UUID actorId, CreateRequirementCommand command) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO requirement_item
                    (id, tenant_id, demand_id, requirement_key, title, description, business_context,
                     ux_context, technical_context, acceptance_criteria, status, created_by,
                     review_note, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, '', ?, ?)
                """, uuidBytes(id), uuidBytes(tenantId), uuidBytes(command.demandId()), command.requirementKey(), command.title(),
                command.description(), command.businessContext(), command.uxContext(), command.technicalContext(),
                command.acceptanceCriteria(), uuidBytes(actorId), Timestamp.from(now), Timestamp.from(now));
        publish(tenantId, actorId, id, "REQUIREMENT_CREATED", now, Map.of("requirementId", id.toString(), "requirementKey", command.requirementKey()));
        return findById(tenantId, id);
    }

    @Override
    public List<RequirementSummary> findAll(UUID tenantId, String status) {
        String sql = baseQuery() + (status == null ? " WHERE r.tenant_id = ?" : " WHERE r.tenant_id = ? AND r.status = ?")
                + " ORDER BY r.updated_at DESC, r.id DESC LIMIT 500";
        return jdbcTemplate.query(sql, (rs, row) -> map(tenantId, rs), status == null ? new Object[]{uuidBytes(tenantId)} : new Object[]{uuidBytes(tenantId), status});
    }

    @Override
    public RequirementSummary updateCheck(UUID tenantId, UUID requirementId, String checkKey, String label,
                                          boolean required, boolean completed, String evidence, UUID actorId) {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO requirement_readiness_check
                    (id, tenant_id, requirement_id, check_key, label, required, completed, evidence, updated_by, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE label = VALUES(label), required = VALUES(required), completed = VALUES(completed),
                    evidence = VALUES(evidence), updated_by = VALUES(updated_by), updated_at = VALUES(updated_at)
                """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), uuidBytes(requirementId), checkKey, label,
                required, completed, evidence, uuidBytes(actorId), Timestamp.from(now));
        jdbcTemplate.update("UPDATE requirement_item SET updated_at = ? WHERE tenant_id = ? AND id = ?",
                Timestamp.from(now), uuidBytes(tenantId), uuidBytes(requirementId));
        publish(tenantId, actorId, requirementId, "REQUIREMENT_READINESS_UPDATED", now,
                Map.of("requirementId", requirementId.toString(), "checkKey", checkKey, "completed", completed));
        return findById(tenantId, requirementId);
    }

    @Override
    public RequirementSummary decide(UUID tenantId, UUID requirementId, UUID reviewerId, String status, String note) {
        if ("READY".equals(status)) {
            Integer incompleteChecks = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM requirement_readiness_check
                    WHERE tenant_id = ? AND requirement_id = ? AND required = TRUE AND completed = FALSE
                    """, Integer.class, uuidBytes(tenantId), uuidBytes(requirementId));
            if (incompleteChecks != null && incompleteChecks > 0) throw new IllegalArgumentException("all required readiness checks must be completed");
            Integer incompleteContext = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM requirement_item
                    WHERE tenant_id = ? AND id = ? AND (business_context = '' OR ux_context = '' OR technical_context = '' OR acceptance_criteria = '')
                    """, Integer.class, uuidBytes(tenantId), uuidBytes(requirementId));
            if (incompleteContext != null && incompleteContext > 0) throw new IllegalArgumentException("all readiness context fields must be completed");
        }
        Instant now = Instant.now();
        int updated = jdbcTemplate.update("""
                UPDATE requirement_item
                SET status = ?, reviewed_by = ?, review_note = ?, reviewed_at = ?, updated_at = ?
                WHERE tenant_id = ? AND id = ? AND status <> 'READY'
                """, status, uuidBytes(reviewerId), note, Timestamp.from(now), Timestamp.from(now), uuidBytes(tenantId), uuidBytes(requirementId));
        if (updated == 0) throw new IllegalArgumentException("requirement was not found or is already ready");
        publish(tenantId, reviewerId, requirementId, "REQUIREMENT_STATUS_CHANGED", now,
                Map.of("requirementId", requirementId.toString(), "status", status, "reviewNote", note));
        return findById(tenantId, requirementId);
    }

    private RequirementSummary findById(UUID tenantId, UUID id) {
        return jdbcTemplate.queryForObject(baseQuery() + " WHERE r.tenant_id = ? AND r.id = ?",
                (rs, row) -> map(tenantId, rs), uuidBytes(tenantId), uuidBytes(id));
    }

    private RequirementSummary map(UUID tenantId, ResultSet rs) throws SQLException {
        Timestamp reviewedAt = rs.getTimestamp("reviewed_at");
        List<ReadinessCheck> checks = jdbcTemplate.query("""
                SELECT id, check_key, label, required, completed, evidence, updated_at
                FROM requirement_readiness_check
                WHERE tenant_id = ? AND requirement_id = ? ORDER BY check_key
                """, (check, row) -> new ReadinessCheck(uuid(check.getBytes("id")), check.getString("check_key"), check.getString("label"),
                check.getBoolean("required"), check.getBoolean("completed"), check.getString("evidence"), check.getTimestamp("updated_at").toInstant()),
                uuidBytes(tenantId), rs.getBytes("id"));
        return new RequirementSummary(uuid(rs.getBytes("id")), rs.getString("requirement_key"), nullableUuid(rs.getBytes("demand_id")),
                rs.getString("title"), rs.getString("description"), rs.getString("business_context"), rs.getString("ux_context"),
                rs.getString("technical_context"), rs.getString("acceptance_criteria"), rs.getString("status"), uuid(rs.getBytes("created_by")),
                nullableUuid(rs.getBytes("reviewed_by")), rs.getString("review_note"), reviewedAt == null ? null : reviewedAt.toInstant(),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(), checks);
    }

    private String baseQuery() {
        return """
                SELECT r.id, r.demand_id, r.requirement_key, r.title, r.description, r.business_context,
                       r.ux_context, r.technical_context, r.acceptance_criteria, r.status, r.created_by,
                       r.reviewed_by, r.review_note, r.reviewed_at, r.created_at, r.updated_at
                FROM requirement_item r
                """;
    }

    private void publish(UUID tenantId, UUID actorId, UUID aggregateId, String type, Instant now, Map<String, Object> payload) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO outbox_event
                        (event_id, tenant_id, event_type, event_version, aggregate_type, aggregate_id,
                         actor_id, correlation_id, causation_id, occurred_at, payload_json, created_at)
                    VALUES (?, ?, ?, 1, 'REQUIREMENT', ?, ?, NULL, NULL, ?, ?, ?)
                    """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), type, uuidBytes(aggregateId), uuidBytes(actorId),
                    Timestamp.from(now), objectMapper.writeValueAsString(payload), Timestamp.from(now));
        } catch (JsonProcessingException exception) { throw new IllegalStateException("Unable to serialize requirement event", exception); }
    }

    private static byte[] uuidBytes(UUID value) { if (value == null) return null; ByteBuffer b = ByteBuffer.allocate(16); b.putLong(value.getMostSignificantBits()); b.putLong(value.getLeastSignificantBits()); return b.array(); }
    private static UUID uuid(byte[] bytes) { ByteBuffer b = ByteBuffer.wrap(bytes); return new UUID(b.getLong(), b.getLong()); }
    private static UUID nullableUuid(byte[] bytes) { return bytes == null ? null : uuid(bytes); }
}
