package com.companyos.persistence.mysql;

import com.companyos.workflow.CreateWorkflowCommand;
import com.companyos.workflow.WorkflowStatusDefinition;
import com.companyos.workflow.WorkflowStore;
import com.companyos.workflow.WorkflowSummary;
import com.companyos.workflow.WorkflowTransitionDefinition;
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
public class MysqlWorkflowStore implements WorkflowStore {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MysqlWorkflowStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<WorkflowSummary> findAll(UUID tenantId) {
        return jdbcTemplate.query("""
                SELECT id, workflow_key, name, methodology, status, created_at
                FROM workflow_definition
                WHERE tenant_id = ? AND status = 'ACTIVE'
                ORDER BY workflow_key
                """, (rs, row) -> mapWorkflow(tenantId, rs), uuidBytes(tenantId));
    }

    @Override
    public WorkflowSummary create(UUID tenantId, UUID actorId, CreateWorkflowCommand command) {
        UUID workflowId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO workflow_definition
                    (id, tenant_id, workflow_key, name, methodology, status, created_by, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                """, uuidBytes(workflowId), uuidBytes(tenantId), command.workflowKey(), command.name(), command.methodology(),
                uuidBytes(actorId), Timestamp.from(now), Timestamp.from(now));
        for (WorkflowStatusDefinition status : command.statuses()) {
            jdbcTemplate.update("""
                    INSERT INTO workflow_status
                        (id, tenant_id, workflow_id, status_key, label, terminal, position, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), uuidBytes(workflowId), status.key(),
                    status.label(), status.terminal(), status.position(), Timestamp.from(now));
        }
        for (WorkflowTransitionDefinition transition : command.transitions()) {
            jdbcTemplate.update("""
                    INSERT INTO workflow_transition
                        (id, tenant_id, workflow_id, from_status_id, to_status_id, created_at)
                    SELECT ?, ?, ?, source.id, target.id, ?
                    FROM workflow_status source
                    JOIN workflow_status target ON target.tenant_id = source.tenant_id
                                              AND target.workflow_id = source.workflow_id
                    WHERE source.tenant_id = ? AND source.workflow_id = ?
                      AND source.status_key = ? AND target.status_key = ?
                    """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), uuidBytes(workflowId), Timestamp.from(now),
                    uuidBytes(tenantId), uuidBytes(workflowId), transition.fromStatus(), transition.toStatus());
        }
        publish(tenantId, actorId, workflowId, "WORKFLOW_CREATED", now,
                Map.of("workflowId", workflowId.toString(), "workflowKey", command.workflowKey()));
        return findById(tenantId, workflowId);
    }

    @Override
    public void assignToProject(UUID tenantId, UUID projectId, UUID workflowId) {
        int updated = jdbcTemplate.update("""
                UPDATE project p
                JOIN workflow_definition w ON w.tenant_id = p.tenant_id AND w.id = ? AND w.status = 'ACTIVE'
                SET p.workflow_id = ?, p.updated_at = CURRENT_TIMESTAMP(6)
                WHERE p.tenant_id = ? AND p.id = ?
                """, uuidBytes(workflowId), uuidBytes(workflowId), uuidBytes(tenantId), uuidBytes(projectId));
        if (updated == 0) throw new IllegalArgumentException("project or workflow was not found");
    }

    @Override
    public boolean transitionAllowed(UUID tenantId, UUID projectId, String fromStatus, String toStatus) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM project p
                JOIN workflow_definition w ON w.tenant_id = p.tenant_id AND w.id = p.workflow_id AND w.status = 'ACTIVE'
                JOIN workflow_transition transition ON transition.tenant_id = w.tenant_id AND transition.workflow_id = w.id
                JOIN workflow_status source ON source.tenant_id = transition.tenant_id AND source.id = transition.from_status_id
                JOIN workflow_status target ON target.tenant_id = transition.tenant_id AND target.id = transition.to_status_id
                WHERE p.tenant_id = ? AND p.id = ? AND source.status_key = ? AND target.status_key = ?
                """, Integer.class, uuidBytes(tenantId), uuidBytes(projectId), fromStatus, toStatus);
        return count != null && count > 0;
    }

    private WorkflowSummary findById(UUID tenantId, UUID workflowId) {
        return jdbcTemplate.queryForObject("""
                SELECT id, workflow_key, name, methodology, status, created_at
                FROM workflow_definition WHERE tenant_id = ? AND id = ?
                """, (rs, row) -> mapWorkflow(tenantId, rs), uuidBytes(tenantId), uuidBytes(workflowId));
    }

    private WorkflowSummary mapWorkflow(UUID tenantId, ResultSet rs) throws SQLException {
        UUID workflowId = uuid(rs.getBytes("id"));
        List<WorkflowStatusDefinition> statuses = jdbcTemplate.query("""
                SELECT status_key, label, terminal, position
                FROM workflow_status WHERE tenant_id = ? AND workflow_id = ? ORDER BY position, status_key
                """, (status, row) -> new WorkflowStatusDefinition(status.getString("status_key"), status.getString("label"),
                status.getBoolean("terminal"), status.getInt("position")), uuidBytes(tenantId), uuidBytes(workflowId));
        List<WorkflowTransitionDefinition> transitions = jdbcTemplate.query("""
                SELECT source.status_key AS from_status, target.status_key AS to_status
                FROM workflow_transition transition
                JOIN workflow_status source ON source.tenant_id = transition.tenant_id AND source.id = transition.from_status_id
                JOIN workflow_status target ON target.tenant_id = transition.tenant_id AND target.id = transition.to_status_id
                WHERE transition.tenant_id = ? AND transition.workflow_id = ? ORDER BY source.position, target.position
                """, (transition, row) -> new WorkflowTransitionDefinition(transition.getString("from_status"), transition.getString("to_status")),
                uuidBytes(tenantId), uuidBytes(workflowId));
        return new WorkflowSummary(workflowId, rs.getString("workflow_key"), rs.getString("name"),
                rs.getString("methodology"), rs.getString("status"), statuses, transitions,
                rs.getTimestamp("created_at").toInstant());
    }

    private void publish(UUID tenantId, UUID actorId, UUID aggregateId, String type, Instant now, Map<String, Object> payload) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO outbox_event
                        (event_id, tenant_id, event_type, event_version, aggregate_type, aggregate_id,
                         actor_id, correlation_id, causation_id, occurred_at, payload_json, created_at)
                    VALUES (?, ?, ?, 1, 'WORKFLOW', ?, ?, NULL, NULL, ?, ?, ?)
                    """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), type, uuidBytes(aggregateId), uuidBytes(actorId),
                    Timestamp.from(now), objectMapper.writeValueAsString(payload), Timestamp.from(now));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize workflow event", exception);
        }
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
