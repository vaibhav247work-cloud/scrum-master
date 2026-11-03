package com.companyos.persistence.mysql;

import com.companyos.work.CreateWorkItemCommand;
import com.companyos.work.WorkItemFilter;
import com.companyos.work.WorkItemStore;
import com.companyos.work.WorkItemSummary;
import com.companyos.work.UpdateWorkItemCommand;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import com.companyos.work.WorkItemAssignmentHistory;
import com.companyos.work.CreateWorkScopeChangeCommand;
import com.companyos.work.WorkScopeChangeRequest;

@Repository
public class MysqlWorkItemStore implements WorkItemStore {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MysqlWorkItemStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public WorkItemSummary create(UUID tenantId, UUID actorId, CreateWorkItemCommand command) {
        UUID workItemId = UUID.randomUUID();
        String workKey = "W-" + workItemId.toString().replace("-", "").substring(0, 12).toUpperCase();
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO work_item
                    (id, tenant_id, work_key, project_id, sprint_id, parent_work_item_id, assignee_user_id, team_id, title, description,
                     acceptance_criteria, estimate_minutes, work_type, priority, status, created_by, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'TODO', ?, ?, ?)
                """, uuidBytes(workItemId), uuidBytes(tenantId), workKey, uuidBytes(command.projectId()),
                uuidBytes(command.sprintId()), uuidBytes(command.parentWorkItemId()), uuidBytes(command.assigneeUserId()), uuidBytes(command.teamId()), command.title(), command.description(),
                command.acceptanceCriteria(), command.estimateMinutes(), command.workType(), command.priority(), uuidBytes(actorId), Timestamp.from(now), Timestamp.from(now));
        long backlogPosition = appendPosition(tenantId, workItemId, command.projectId(), command.sprintId());
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("workItemId", workItemId.toString());
            payload.put("workKey", workKey);
            payload.put("title", command.title());
            if (command.projectId() != null) payload.put("projectId", command.projectId().toString());
            if (command.sprintId() != null) payload.put("sprintId", command.sprintId().toString());
            if (command.teamId() != null) payload.put("teamId", command.teamId().toString());
            String eventPayload = objectMapper.writeValueAsString(payload);
            jdbcTemplate.update("""
                    INSERT INTO outbox_event
                        (event_id, tenant_id, event_type, event_version, aggregate_type, aggregate_id,
                         actor_id, correlation_id, causation_id, occurred_at, payload_json, created_at)
                    VALUES (?, ?, 'WORK_ITEM_CREATED', 1, 'WORK_ITEM', ?, ?, NULL, NULL, ?, ?, ?)
                    """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), uuidBytes(workItemId), uuidBytes(actorId),
                    Timestamp.from(now), eventPayload, Timestamp.from(now));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize work item event", exception);
        }
                return new WorkItemSummary(workItemId, workKey, command.projectId(), command.sprintId(),
                command.assigneeUserId(), command.title(), command.description(), command.workType(),
                command.priority(), "TODO", now, command.teamId(), command.estimateMinutes(), null,
                command.parentWorkItemId(), command.acceptanceCriteria(), backlogPosition);
    }

    @Override
    public List<WorkItemSummary> find(UUID tenantId, WorkItemFilter filter) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, work_key, project_id, sprint_id, parent_work_item_id, assignee_user_id, team_id, title, description, acceptance_criteria,
                       backlog_position,
                       estimate_minutes, work_type, priority, status, created_at, completed_at
                FROM work_item
                WHERE tenant_id = ?
                """);
        List<Object> arguments = new ArrayList<>();
        arguments.add(uuidBytes(tenantId));
        if (filter.projectId() != null) {
            sql.append(" AND project_id = ?");
            arguments.add(uuidBytes(filter.projectId()));
        }
        if (filter.sprintId() != null) {
            sql.append(" AND sprint_id = ?");
            arguments.add(uuidBytes(filter.sprintId()));
        }
        if (filter.assigneeUserId() != null) {
            sql.append(" AND assignee_user_id = ?");
            arguments.add(uuidBytes(filter.assigneeUserId()));
        }
        if (filter.teamId() != null) {
            sql.append(" AND team_id = ?");
            arguments.add(uuidBytes(filter.teamId()));
        }
        sql.append(" ORDER BY backlog_position ASC, created_at ASC, id ASC LIMIT 100");
        return jdbcTemplate.query(sql.toString(), this::map, arguments.toArray());
    }

    @Override
    public Optional<WorkItemSummary> findById(UUID tenantId, UUID workItemId) {
        List<WorkItemSummary> result = jdbcTemplate.query("""
                SELECT id, work_key, project_id, sprint_id, parent_work_item_id, assignee_user_id, team_id, title, description, acceptance_criteria,
                       backlog_position,
                       estimate_minutes, work_type, priority, status, created_at, completed_at
                FROM work_item
                WHERE tenant_id = ? AND id = ?
                """, this::map, uuidBytes(tenantId), uuidBytes(workItemId));
        return result.stream().findFirst();
    }

    @Override
    public WorkItemSummary update(UUID tenantId, UUID actorId, UUID workItemId, UpdateWorkItemCommand command) {
        Instant now = Instant.now();
        UUID previousAssignee = jdbcTemplate.query("SELECT assignee_user_id FROM work_item WHERE tenant_id = ? AND id = ? FOR UPDATE",
                rs -> rs.next() ? nullableUuid(rs.getBytes("assignee_user_id")) : null,
                uuidBytes(tenantId), uuidBytes(workItemId));
        if (command.assigneeUserId() != null) {
            Boolean active = jdbcTemplate.query("SELECT status FROM tenant_user WHERE tenant_id = ? AND id = ?",
                    rs -> rs.next() && "ACTIVE".equals(rs.getString("status")), uuidBytes(tenantId), uuidBytes(command.assigneeUserId()));
            if (!Boolean.TRUE.equals(active)) throw new IllegalArgumentException("assignee must be an active tenant user");
        }
        int changed = jdbcTemplate.update("""
                UPDATE work_item
                SET parent_work_item_id = ?, assignee_user_id = ?, team_id = ?, title = ?, description = ?, acceptance_criteria = ?, estimate_minutes = ?, work_type = ?, priority = ?,
                    completed_at = CASE WHEN ? = 'DONE' THEN COALESCE(completed_at, ?) ELSE NULL END,
                    status = ?, updated_at = ?
                WHERE tenant_id = ? AND id = ?
                """, uuidBytes(command.parentWorkItemId()), uuidBytes(command.assigneeUserId()), uuidBytes(command.teamId()), command.title(), command.description(), command.acceptanceCriteria(), command.estimateMinutes(), command.workType(),
                command.priority(), command.status(), Timestamp.from(now), command.status(), Timestamp.from(now), command.status(), Timestamp.from(now), uuidBytes(tenantId), uuidBytes(workItemId));
        if (changed != 1) throw new IllegalArgumentException("work item was not found");
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("workItemId", workItemId.toString());
            payload.put("status", command.status());
            payload.put("assigneeUserId", command.assigneeUserId() == null ? null : command.assigneeUserId().toString());
            payload.put("teamId", command.teamId() == null ? null : command.teamId().toString());
            boolean reassigned = !Objects.equals(previousAssignee, command.assigneeUserId());
            if (reassigned) {
                payload.put("previousAssigneeUserId", previousAssignee == null ? null : previousAssignee.toString());
                payload.put("newAssigneeUserId", command.assigneeUserId() == null ? null : command.assigneeUserId().toString());
                payload.put("reason", command.assignmentReason());
                payload.put("title", command.title());
                jdbcTemplate.update("""
                        INSERT INTO work_item_assignment_history
                            (id, tenant_id, work_item_id, previous_assignee_user_id, new_assignee_user_id,
                             changed_by, reason, changed_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), uuidBytes(workItemId),
                        uuidBytes(previousAssignee), uuidBytes(command.assigneeUserId()), uuidBytes(actorId),
                        command.assignmentReason(), Timestamp.from(now));
            }
            jdbcTemplate.update("""
                    INSERT INTO outbox_event
                        (event_id, tenant_id, event_type, event_version, aggregate_type, aggregate_id,
                         actor_id, correlation_id, causation_id, occurred_at, payload_json, created_at)
                    VALUES (?, ?, ?, 1, 'WORK_ITEM', ?, ?, NULL, NULL, ?, ?, ?)
                    """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), reassigned ? "WORK_ITEM_REASSIGNED" : "WORK_ITEM_UPDATED", uuidBytes(workItemId), uuidBytes(actorId),
                    Timestamp.from(now), objectMapper.writeValueAsString(payload), Timestamp.from(now));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize work item update event", exception);
        }
        return findById(tenantId, workItemId).orElseThrow(() -> new IllegalArgumentException("work item was not found"));
    }

    @Override
    public WorkItemSummary reorder(UUID tenantId, UUID actorId, UUID workItemId, int targetPosition) {
        WorkItemSummary current = findById(tenantId, workItemId)
                .orElseThrow(() -> new IllegalArgumentException("work item was not found"));
        List<UUID> orderedIds = jdbcTemplate.query("""
                SELECT id
                FROM work_item
                WHERE tenant_id = ? AND project_id <=> ? AND sprint_id <=> ?
                ORDER BY backlog_position ASC, created_at ASC, id ASC
                FOR UPDATE
                """, (rs, row) -> uuid(rs.getBytes("id")), uuidBytes(tenantId),
                uuidBytes(current.projectId()), uuidBytes(current.sprintId()));
        int oldPosition = orderedIds.indexOf(workItemId);
        if (oldPosition < 0) throw new IllegalArgumentException("work item was not found");
        int boundedPosition = Math.min(targetPosition, orderedIds.size() - 1);
        orderedIds.remove(oldPosition);
        orderedIds.add(boundedPosition, workItemId);
        Instant now = Instant.now();
        for (int index = 0; index < orderedIds.size(); index++) {
            jdbcTemplate.update("UPDATE work_item SET backlog_position = ?, updated_at = ? WHERE tenant_id = ? AND id = ?",
                    (index + 1L) * 1000L, Timestamp.from(now), uuidBytes(tenantId), uuidBytes(orderedIds.get(index)));
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("workItemId", workItemId.toString());
            payload.put("oldPosition", oldPosition);
            payload.put("newPosition", boundedPosition);
            String eventPayload = objectMapper.writeValueAsString(payload);
            jdbcTemplate.update("""
                    INSERT INTO audit_event (id, tenant_id, actor_id, event_type, aggregate_type, aggregate_id,
                                             correlation_id, occurred_at, payload_json)
                    VALUES (?, ?, ?, 'WORK_ITEM_REORDERED', 'WORK_ITEM', ?, NULL, ?, CAST(? AS JSON))
                    """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), uuidBytes(actorId), uuidBytes(workItemId),
                    Timestamp.from(now), eventPayload);
            jdbcTemplate.update("""
                    INSERT INTO outbox_event
                        (event_id, tenant_id, event_type, event_version, aggregate_type, aggregate_id,
                         actor_id, correlation_id, causation_id, occurred_at, payload_json, created_at)
                    VALUES (?, ?, 'WORK_ITEM_REORDERED', 1, 'WORK_ITEM', ?, ?, NULL, NULL, ?, CAST(? AS JSON), ?)
                    """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), uuidBytes(workItemId), uuidBytes(actorId),
                    Timestamp.from(now), eventPayload, Timestamp.from(now));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize work item reorder event", exception);
        }
        return findById(tenantId, workItemId).orElseThrow(() -> new IllegalArgumentException("work item was not found"));
    }

    @Override
    public List<WorkItemAssignmentHistory> assignmentHistory(UUID tenantId, UUID workItemId) {
        return jdbcTemplate.query("""
                SELECT id, work_item_id, previous_assignee_user_id, new_assignee_user_id,
                       changed_by, reason, changed_at
                FROM work_item_assignment_history
                WHERE tenant_id = ? AND work_item_id = ?
                ORDER BY changed_at DESC, id DESC
                LIMIT 100
                """, (rs, row) -> new WorkItemAssignmentHistory(uuid(rs.getBytes("id")),
                uuid(rs.getBytes("work_item_id")), nullableUuid(rs.getBytes("previous_assignee_user_id")),
                nullableUuid(rs.getBytes("new_assignee_user_id")), uuid(rs.getBytes("changed_by")),
                rs.getString("reason"), rs.getTimestamp("changed_at").toInstant()),
                uuidBytes(tenantId), uuidBytes(workItemId));
    }

    @Override
    public WorkScopeChangeRequest createScopeChangeRequest(UUID tenantId, UUID actorId,
                                                            CreateWorkScopeChangeCommand command) {
        ScopeContext context = jdbcTemplate.query("""
                SELECT project_id FROM work_item WHERE tenant_id = ? AND id = ?
                """, rs -> rs.next() ? new ScopeContext(nullableUuid(rs.getBytes("project_id"))) : null,
                uuidBytes(tenantId), uuidBytes(command.workItemId()));
        if (context == null) throw new IllegalArgumentException("work item was not found");
        if (command.targetSprintId() != null) {
            if (context.projectId() == null) throw new IllegalArgumentException("target sprint requires a project work item");
            Boolean validTarget = jdbcTemplate.query("""
                    SELECT status FROM sprint
                    WHERE tenant_id = ? AND project_id = ? AND id = ?
                    """, rs -> rs.next() && Set.of("PLANNED", "ACTIVE").contains(rs.getString("status")),
                    uuidBytes(tenantId), uuidBytes(context.projectId()), uuidBytes(command.targetSprintId()));
            if (!Boolean.TRUE.equals(validTarget)) throw new IllegalArgumentException("target sprint was not found or is not open");
        }
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO work_scope_change_request
                    (id, tenant_id, work_item_id, project_id, requested_priority, target_sprint_id,
                     requested_by, emergency, reason, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'REQUESTED', ?)
                """, uuidBytes(id), uuidBytes(tenantId), uuidBytes(command.workItemId()), uuidBytes(context.projectId()),
                command.requestedPriority(), uuidBytes(command.targetSprintId()), uuidBytes(actorId), command.emergency(),
                command.reason(), Timestamp.from(now));
        recordScopeEvent(tenantId, actorId, command.workItemId(), "WORK_SCOPE_CHANGE_REQUESTED", Map.of(
                "requestId", id.toString(), "workItemId", command.workItemId().toString(),
                "requestedPriority", command.requestedPriority() == null ? "" : command.requestedPriority(),
                "targetSprintId", command.targetSprintId() == null ? "" : command.targetSprintId().toString(),
                "emergency", command.emergency(), "reason", command.reason()), now);
        return new WorkScopeChangeRequest(id, command.workItemId(), context.projectId(), command.requestedPriority(),
                command.targetSprintId(), actorId, command.emergency(), command.reason(), "REQUESTED", null, "", now, null);
    }

    @Override
    public List<WorkScopeChangeRequest> scopeChangeRequests(UUID tenantId, UUID workItemId) {
        return jdbcTemplate.query("""
                SELECT id, work_item_id, project_id, requested_priority, target_sprint_id, requested_by,
                       emergency, reason, status, decided_by, decision_note, created_at, decided_at
                FROM work_scope_change_request
                WHERE tenant_id = ? AND work_item_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT 100
                """, this::mapScopeRequest, uuidBytes(tenantId), uuidBytes(workItemId));
    }

    @Override
    public List<WorkScopeChangeRequest> pendingScopeChangeRequests(UUID tenantId) {
        return jdbcTemplate.query("""
                SELECT id, work_item_id, project_id, requested_priority, target_sprint_id, requested_by,
                       emergency, reason, status, decided_by, decision_note, created_at, decided_at
                FROM work_scope_change_request
                WHERE tenant_id = ? AND status = 'REQUESTED'
                ORDER BY emergency DESC, created_at ASC, id ASC
                LIMIT 200
                """, this::mapScopeRequest, uuidBytes(tenantId));
    }

    @Override
    public WorkScopeChangeRequest decideScopeChangeRequest(UUID tenantId, UUID requestId, UUID actorId,
                                                            boolean approve, String decisionNote) {
        WorkScopeChangeRequest request = jdbcTemplate.query("""
                SELECT id, work_item_id, project_id, requested_priority, target_sprint_id, requested_by,
                       emergency, reason, status, decided_by, decision_note, created_at, decided_at
                FROM work_scope_change_request WHERE tenant_id = ? AND id = ? FOR UPDATE
                """, (org.springframework.jdbc.core.ResultSetExtractor<WorkScopeChangeRequest>) rs -> rs.next() ? mapScopeRequest(rs, 0) : null,
                uuidBytes(tenantId), uuidBytes(requestId));
        if (request == null) throw new IllegalArgumentException("scope change request was not found");
        if (!"REQUESTED".equals(request.status())) throw new IllegalArgumentException("scope change request was already decided");
        Instant now = Instant.now();
        String status = approve ? "APPROVED" : "REJECTED";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", request.id().toString());
        payload.put("workItemId", request.workItemId().toString());
        payload.put("requestedBy", request.requestedBy().toString());
        payload.put("emergency", request.emergency());
        payload.put("decisionNote", decisionNote);
        if (approve) {
            WorkChangeState current = jdbcTemplate.query("""
                    SELECT priority, sprint_id, assignee_user_id, title
                    FROM work_item WHERE tenant_id = ? AND id = ? FOR UPDATE
                    """, (org.springframework.jdbc.core.ResultSetExtractor<WorkChangeState>) rs -> rs.next()
                            ? new WorkChangeState(rs.getString("priority"), nullableUuid(rs.getBytes("sprint_id")),
                            nullableUuid(rs.getBytes("assignee_user_id")), rs.getString("title")) : null,
                    uuidBytes(tenantId), uuidBytes(request.workItemId()));
            if (current == null) throw new IllegalArgumentException("work item was not found");
            jdbcTemplate.update("""
                    UPDATE work_item SET priority = COALESCE(?, priority), sprint_id = COALESCE(?, sprint_id), updated_at = ?
                    WHERE tenant_id = ? AND id = ?
                    """, request.requestedPriority(), uuidBytes(request.targetSprintId()), Timestamp.from(now),
                    uuidBytes(tenantId), uuidBytes(request.workItemId()));
            payload.put("oldPriority", current.priority());
            payload.put("newPriority", request.requestedPriority() == null ? current.priority() : request.requestedPriority());
            payload.put("oldSprintId", current.sprintId() == null ? "" : current.sprintId().toString());
            payload.put("newSprintId", request.targetSprintId() == null ? (current.sprintId() == null ? "" : current.sprintId().toString()) : request.targetSprintId().toString());
            payload.put("assigneeUserId", current.assigneeUserId() == null ? "" : current.assigneeUserId().toString());
            payload.put("title", current.title());
        }
        jdbcTemplate.update("""
                UPDATE work_scope_change_request
                SET status = ?, decided_by = ?, decision_note = ?, decided_at = ?
                WHERE tenant_id = ? AND id = ? AND status = 'REQUESTED'
                """, status, uuidBytes(actorId), decisionNote, Timestamp.from(now), uuidBytes(tenantId), uuidBytes(requestId));
        payload.put("status", status);
        recordScopeEvent(tenantId, actorId, request.workItemId(), approve ? "WORK_SCOPE_CHANGED" : "WORK_SCOPE_CHANGE_REJECTED", payload, now);
        return new WorkScopeChangeRequest(request.id(), request.workItemId(), request.projectId(), request.requestedPriority(),
                request.targetSprintId(), request.requestedBy(), request.emergency(), request.reason(), status, actorId,
                decisionNote, request.createdAt(), now);
    }

    private WorkScopeChangeRequest singleScopeRequest(ResultSet rs, int row) throws SQLException {
        return rs.next() ? mapScopeRequest(rs, row) : null;
    }

    private WorkScopeChangeRequest mapScopeRequest(ResultSet rs, int row) throws SQLException {
        Timestamp decidedAt = rs.getTimestamp("decided_at");
        return new WorkScopeChangeRequest(uuid(rs.getBytes("id")), uuid(rs.getBytes("work_item_id")),
                nullableUuid(rs.getBytes("project_id")), rs.getString("requested_priority"),
                nullableUuid(rs.getBytes("target_sprint_id")), uuid(rs.getBytes("requested_by")), rs.getBoolean("emergency"),
                rs.getString("reason"), rs.getString("status"), nullableUuid(rs.getBytes("decided_by")),
                rs.getString("decision_note"), rs.getTimestamp("created_at").toInstant(), decidedAt == null ? null : decidedAt.toInstant());
    }

    private WorkChangeState singleChangeState(ResultSet rs, int row) throws SQLException {
        return rs.next() ? new WorkChangeState(rs.getString("priority"), nullableUuid(rs.getBytes("sprint_id")),
                nullableUuid(rs.getBytes("assignee_user_id")), rs.getString("title")) : null;
    }

    private void recordScopeEvent(UUID tenantId, UUID actorId, UUID aggregateId, String eventType,
                                  Map<String, Object> values, Instant now) {
        try {
            String payload = objectMapper.writeValueAsString(values);
            jdbcTemplate.update("""
                    INSERT INTO audit_event (id, tenant_id, actor_id, event_type, aggregate_type, aggregate_id,
                                             correlation_id, occurred_at, payload_json)
                    VALUES (?, ?, ?, ?, 'WORK_ITEM', ?, NULL, ?, CAST(? AS JSON))
                    """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), uuidBytes(actorId), eventType,
                    uuidBytes(aggregateId), Timestamp.from(now), payload);
            jdbcTemplate.update("""
                    INSERT INTO outbox_event (event_id, tenant_id, event_type, event_version, aggregate_type, aggregate_id,
                                              actor_id, correlation_id, causation_id, occurred_at, payload_json, created_at)
                    VALUES (?, ?, ?, 1, 'WORK_ITEM', ?, ?, NULL, NULL, ?, CAST(? AS JSON), ?)
                    """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), eventType, uuidBytes(aggregateId),
                    uuidBytes(actorId), Timestamp.from(now), payload, Timestamp.from(now));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize scope change event", exception);
        }
    }

    private record ScopeContext(UUID projectId) { }
    private record WorkChangeState(String priority, UUID sprintId, UUID assigneeUserId, String title) { }

    private WorkItemSummary map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new WorkItemSummary(uuid(resultSet.getBytes("id")), resultSet.getString("work_key"),
                nullableUuid(resultSet.getBytes("project_id")), nullableUuid(resultSet.getBytes("sprint_id")),
                nullableUuid(resultSet.getBytes("assignee_user_id")), resultSet.getString("title"),
                resultSet.getString("description"), resultSet.getString("work_type"),
                resultSet.getString("priority"), resultSet.getString("status"),
                resultSet.getTimestamp("created_at").toInstant(), nullableUuid(resultSet.getBytes("team_id")),
                (Integer) resultSet.getObject("estimate_minutes"),
                resultSet.getTimestamp("completed_at") == null ? null : resultSet.getTimestamp("completed_at").toInstant(),
                nullableUuid(resultSet.getBytes("parent_work_item_id")),
                resultSet.getString("acceptance_criteria") == null ? "" : resultSet.getString("acceptance_criteria"),
                (Long) resultSet.getObject("backlog_position"));
    }

    private long appendPosition(UUID tenantId, UUID workItemId, UUID projectId, UUID sprintId) {
        Long maximum = jdbcTemplate.queryForObject("""
                SELECT MAX(backlog_position)
                FROM work_item
                WHERE tenant_id = ? AND project_id <=> ? AND sprint_id <=> ? AND id <> ?
                """, Long.class, uuidBytes(tenantId), uuidBytes(projectId), uuidBytes(sprintId), uuidBytes(workItemId));
        long position = maximum == null ? 1000L : maximum + 1000L;
        jdbcTemplate.update("UPDATE work_item SET backlog_position = ? WHERE tenant_id = ? AND id = ?",
                position, uuidBytes(tenantId), uuidBytes(workItemId));
        return position;
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
