package com.companyos.persistence.mysql;

import com.companyos.time.CreateTimeEntryCommand;
import com.companyos.time.TimeEntryStore;
import com.companyos.time.TimeEntrySummary;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class MysqlTimeEntryStore implements TimeEntryStore {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MysqlTimeEntryStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public TimeEntrySummary create(UUID tenantId, UUID userId, CreateTimeEntryCommand command) {
        UUID timeEntryId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO time_entry
                    (id, tenant_id, user_id, work_item_id, project_id, sprint_id,
                     entry_date, minutes, description, source, category_key, created_at, approval_status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, uuidBytes(timeEntryId), uuidBytes(tenantId), uuidBytes(userId),
                uuidBytes(command.workItemId()), uuidBytes(command.projectId()), uuidBytes(command.sprintId()),
                Date.valueOf(command.entryDate()), command.minutes(), command.description(), command.source(),
                command.categoryKey(), Timestamp.from(now), command.approvalStatus());


        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("timeEntryId", timeEntryId.toString());
            payload.put("userId", userId.toString());
            payload.put("entryDate", command.entryDate().toString());
            payload.put("minutes", command.minutes());
            payload.put("source", command.source());
            if (command.workItemId() != null) payload.put("workItemId", command.workItemId().toString());
            if (command.projectId() != null) payload.put("projectId", command.projectId().toString());
            if (command.sprintId() != null) payload.put("sprintId", command.sprintId().toString());
            String eventPayload = objectMapper.writeValueAsString(payload);
            jdbcTemplate.update("""
                    INSERT INTO outbox_event
                        (event_id, tenant_id, event_type, event_version, aggregate_type, aggregate_id,
                         actor_id, correlation_id, causation_id, occurred_at, payload_json, created_at)
                    VALUES (?, ?, 'TIME_ENTRY_CREATED', 1, 'TIME_ENTRY', ?, ?, NULL, NULL, ?, ?, ?)
                    """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), uuidBytes(timeEntryId),
                    uuidBytes(userId), Timestamp.from(now), eventPayload, Timestamp.from(now));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize time entry event", exception);
        }

        return new TimeEntrySummary(timeEntryId, userId, command.workItemId(), command.projectId(), command.sprintId(),
                command.entryDate(), command.minutes(), command.description(), command.source(), command.categoryKey(), now,
                command.approvalStatus(), null, null, "");
    }

    @Override
    public List<TimeEntrySummary> findForUser(UUID tenantId, UUID userId) {
        return jdbcTemplate.query("""
                SELECT id, user_id, work_item_id, project_id, sprint_id, entry_date,
                       minutes, description, source, category_key, created_at, approval_status, approved_by, approved_at, approval_note
                FROM time_entry
                WHERE tenant_id = ? AND user_id = ?
                ORDER BY entry_date DESC, created_at DESC, id DESC
                LIMIT 100
                """, this::map, uuidBytes(tenantId), uuidBytes(userId));
    }

    @Override
    public List<TimeEntrySummary> findPending(UUID tenantId) {
        return jdbcTemplate.query("""
                SELECT id, user_id, work_item_id, project_id, sprint_id, entry_date,
                       minutes, description, source, category_key, created_at, approval_status, approved_by, approved_at, approval_note
                FROM time_entry
                WHERE tenant_id = ? AND approval_status = 'PENDING'
                ORDER BY entry_date ASC, created_at ASC, id ASC
                LIMIT 500
                """, this::map, uuidBytes(tenantId));
    }

    @Override
    public TimeEntrySummary decide(UUID tenantId, UUID entryId, UUID approverId, boolean approved, String note) {
        Instant decidedAt = Instant.now();
        int updated = jdbcTemplate.update("""
                UPDATE time_entry
                SET approval_status = ?, approved_by = ?, approved_at = ?, approval_note = ?
                WHERE tenant_id = ? AND id = ? AND approval_status = 'PENDING'
                """, approved ? "APPROVED" : "REJECTED", uuidBytes(approverId), Timestamp.from(decidedAt), note,
                uuidBytes(tenantId), uuidBytes(entryId));
        if (updated == 0) throw new IllegalArgumentException("pending time entry was not found");
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("timeEntryId", entryId.toString());
            payload.put("approverId", approverId.toString());
            payload.put("approved", approved);
            payload.put("note", note);
            jdbcTemplate.update("""
                    INSERT INTO outbox_event
                        (event_id, tenant_id, event_type, event_version, aggregate_type, aggregate_id,
                         actor_id, correlation_id, causation_id, occurred_at, payload_json, created_at)
                    VALUES (?, ?, ?, 1, 'TIME_ENTRY', ?, ?, NULL, NULL, ?, ?, ?)
                    """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId),
                    approved ? "TIME_ENTRY_APPROVED" : "TIME_ENTRY_REJECTED", uuidBytes(entryId),
                    uuidBytes(approverId), Timestamp.from(decidedAt), objectMapper.writeValueAsString(payload),
                    Timestamp.from(decidedAt));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize time approval event", exception);
        }
        return jdbcTemplate.queryForObject("""
                SELECT id, user_id, work_item_id, project_id, sprint_id, entry_date,
                       minutes, description, source, created_at, approval_status, approved_by, approved_at, approval_note
                FROM time_entry WHERE tenant_id = ? AND id = ?
                """, this::map, uuidBytes(tenantId), uuidBytes(entryId));
    }

    @Override
    public int sumMinutesForUserOnDate(UUID tenantId, UUID userId, LocalDate entryDate) {
        // This is called inside the time-entry transaction. Lock the tenant/user/date
        // index range before calculating the total so concurrent submissions cannot
        // both pass the daily-limit check and then write over the limit.
        List<Integer> minutes = jdbcTemplate.query("""
                SELECT minutes
                FROM time_entry
                WHERE tenant_id = ? AND user_id = ? AND entry_date = ?
                ORDER BY id
                FOR UPDATE
                """, (resultSet, rowNumber) -> resultSet.getInt("minutes"),
                uuidBytes(tenantId), uuidBytes(userId), Date.valueOf(entryDate));
        return minutes.stream().mapToInt(Integer::intValue).sum();
    }

    private TimeEntrySummary map(ResultSet resultSet, int rowNumber) throws SQLException {
        Date entryDate = resultSet.getDate("entry_date");
        return new TimeEntrySummary(
                uuid(resultSet.getBytes("id")),
                uuid(resultSet.getBytes("user_id")),
                nullableUuid(resultSet.getBytes("work_item_id")),
                nullableUuid(resultSet.getBytes("project_id")),
                nullableUuid(resultSet.getBytes("sprint_id")),
                entryDate.toLocalDate(),
                resultSet.getInt("minutes"),
                resultSet.getString("description"),
                resultSet.getString("source"), resultSet.getString("category_key"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getString("approval_status"), nullableUuid(resultSet.getBytes("approved_by")),
                resultSet.getTimestamp("approved_at") == null ? null : resultSet.getTimestamp("approved_at").toInstant(),
                resultSet.getString("approval_note"));
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
