package com.companyos.persistence.mysql;

import com.companyos.quality.CreateIncidentCommand;
import com.companyos.quality.CreateQualityGateCommand;
import com.companyos.quality.IncidentSummary;
import com.companyos.quality.QualityGateSummary;
import com.companyos.quality.QualityOperationsStore;
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
public class MysqlQualityOperationsStore implements QualityOperationsStore {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MysqlQualityOperationsStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public QualityGateSummary createGate(UUID tenantId, UUID actorId, CreateQualityGateCommand command) {
        UUID id = UUID.randomUUID(); Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO quality_gate
                    (id, tenant_id, project_id, release_id, gate_key, gate_type, status, required, evidence, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'OPEN', ?, '', ?, ?)
                """, uuidBytes(id), uuidBytes(tenantId), uuidBytes(command.projectId()), uuidBytes(command.releaseId()),
                command.gateKey(), command.gateType(), command.required(), Timestamp.from(now), Timestamp.from(now));
        publish(tenantId, actorId, id, "QUALITY_GATE_CREATED", now, Map.of("gateId", id.toString(), "gateKey", command.gateKey()));
        return findGate(tenantId, id);
    }

    @Override
    public List<QualityGateSummary> findGates(UUID tenantId, UUID projectId, UUID releaseId) {
        StringBuilder sql = new StringBuilder(baseGateQuery()).append(" WHERE tenant_id = ? ");
        java.util.ArrayList<Object> args = new java.util.ArrayList<>(); args.add(uuidBytes(tenantId));
        if (projectId != null) { sql.append(" AND project_id = ? "); args.add(uuidBytes(projectId)); }
        if (releaseId != null) { sql.append(" AND release_id = ? "); args.add(uuidBytes(releaseId)); }
        sql.append(" ORDER BY updated_at DESC, id DESC LIMIT 500");
        return jdbcTemplate.query(sql.toString(), this::mapGate, args.toArray());
    }

    @Override
    public QualityGateSummary decideGate(UUID tenantId, UUID actorId, UUID gateId, String status, String evidence) {
        Instant now = Instant.now();
        int updated = jdbcTemplate.update("""
                UPDATE quality_gate SET status = ?, evidence = ?, decided_by = ?, decided_at = ?, updated_at = ?
                WHERE tenant_id = ? AND id = ? AND status IN ('OPEN', 'FAILED')
                """, status, evidence, uuidBytes(actorId), Timestamp.from(now), Timestamp.from(now), uuidBytes(tenantId), uuidBytes(gateId));
        if (updated == 0) throw new IllegalArgumentException("quality gate was not found or already decided");
        publish(tenantId, actorId, gateId, "QUALITY_GATE_DECIDED", now, Map.of("gateId", gateId.toString(), "status", status));
        return findGate(tenantId, gateId);
    }

    @Override
    public IncidentSummary createIncident(UUID tenantId, UUID actorId, CreateIncidentCommand command) {
        UUID id = UUID.randomUUID(); Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO operations_incident
                    (id, tenant_id, project_id, release_id, incident_key, title, description, severity, status,
                     owner_user_id, mitigation, root_cause, opened_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', ?, '', '', ?, ?)
                """, uuidBytes(id), uuidBytes(tenantId), uuidBytes(command.projectId()), uuidBytes(command.releaseId()),
                command.incidentKey(), command.title(), command.description(), command.severity(), uuidBytes(command.ownerUserId()),
                Timestamp.from(now), Timestamp.from(now));
        publish(tenantId, actorId, id, "INCIDENT_CREATED", now, Map.of("incidentId", id.toString(), "incidentKey", command.incidentKey(), "severity", command.severity()));
        return findIncident(tenantId, id);
    }

    @Override
    public List<IncidentSummary> findIncidents(UUID tenantId, String status) {
        if (status == null) return jdbcTemplate.query(baseIncidentQuery() + " WHERE tenant_id = ? ORDER BY updated_at DESC, id DESC LIMIT 500", this::mapIncident, uuidBytes(tenantId));
        return jdbcTemplate.query(baseIncidentQuery() + " WHERE tenant_id = ? AND status = ? ORDER BY updated_at DESC, id DESC LIMIT 500", this::mapIncident, uuidBytes(tenantId), status);
    }

    @Override
    public IncidentSummary updateIncident(UUID tenantId, UUID actorId, UUID incidentId, String status, String mitigation, String rootCause) {
        Instant now = Instant.now();
        int updated = jdbcTemplate.update("""
                UPDATE operations_incident SET status = ?, mitigation = ?, root_cause = ?, updated_at = ?
                WHERE tenant_id = ? AND id = ?
                """, status, mitigation, rootCause, Timestamp.from(now), uuidBytes(tenantId), uuidBytes(incidentId));
        if (updated == 0) throw new IllegalArgumentException("incident was not found");
        publish(tenantId, actorId, incidentId, "INCIDENT_STATUS_CHANGED", now, Map.of("incidentId", incidentId.toString(), "status", status));
        return findIncident(tenantId, incidentId);
    }

    private QualityGateSummary findGate(UUID tenantId, UUID id) { return jdbcTemplate.queryForObject(baseGateQuery() + " WHERE tenant_id = ? AND id = ?", this::mapGate, uuidBytes(tenantId), uuidBytes(id)); }
    private IncidentSummary findIncident(UUID tenantId, UUID id) { return jdbcTemplate.queryForObject(baseIncidentQuery() + " WHERE tenant_id = ? AND id = ?", this::mapIncident, uuidBytes(tenantId), uuidBytes(id)); }
    private String baseGateQuery() { return "SELECT id, project_id, release_id, gate_key, gate_type, status, required, evidence, decided_by, decided_at, created_at, updated_at FROM quality_gate"; }
    private String baseIncidentQuery() { return "SELECT id, project_id, release_id, incident_key, title, description, severity, status, owner_user_id, mitigation, root_cause, opened_at, updated_at FROM operations_incident"; }
    private QualityGateSummary mapGate(ResultSet rs, int row) throws SQLException { Timestamp decided = rs.getTimestamp("decided_at"); return new QualityGateSummary(uuid(rs.getBytes("id")), nullableUuid(rs.getBytes("project_id")), nullableUuid(rs.getBytes("release_id")), rs.getString("gate_key"), rs.getString("gate_type"), rs.getString("status"), rs.getBoolean("required"), rs.getString("evidence"), nullableUuid(rs.getBytes("decided_by")), decided == null ? null : decided.toInstant(), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()); }
    private IncidentSummary mapIncident(ResultSet rs, int row) throws SQLException { return new IncidentSummary(uuid(rs.getBytes("id")), nullableUuid(rs.getBytes("project_id")), nullableUuid(rs.getBytes("release_id")), rs.getString("incident_key"), rs.getString("title"), rs.getString("description"), rs.getString("severity"), rs.getString("status"), nullableUuid(rs.getBytes("owner_user_id")), rs.getString("mitigation"), rs.getString("root_cause"), rs.getTimestamp("opened_at").toInstant(), rs.getTimestamp("updated_at").toInstant()); }
    private void publish(UUID tenantId, UUID actorId, UUID aggregateId, String type, Instant now, Map<String, Object> payload) { try { jdbcTemplate.update("""
            INSERT INTO outbox_event (event_id, tenant_id, event_type, event_version, aggregate_type, aggregate_id, actor_id, correlation_id, causation_id, occurred_at, payload_json, created_at)
            VALUES (?, ?, ?, 1, 'QUALITY_OPERATIONS', ?, ?, NULL, NULL, ?, ?, ?)
            """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), type, uuidBytes(aggregateId), uuidBytes(actorId), Timestamp.from(now), objectMapper.writeValueAsString(payload), Timestamp.from(now)); } catch (JsonProcessingException exception) { throw new IllegalStateException("Unable to serialize quality event", exception); } }
    private static byte[] uuidBytes(UUID value) { if (value == null) return null; ByteBuffer b = ByteBuffer.allocate(16); b.putLong(value.getMostSignificantBits()); b.putLong(value.getLeastSignificantBits()); return b.array(); }
    private static UUID uuid(byte[] bytes) { ByteBuffer b = ByteBuffer.wrap(bytes); return new UUID(b.getLong(), b.getLong()); }
    private static UUID nullableUuid(byte[] bytes) { return bytes == null ? null : uuid(bytes); }
}
