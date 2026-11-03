package com.companyos.persistence.mysql;

import com.companyos.time.TimesheetStatus;
import com.companyos.time.TimesheetStore;
import com.companyos.time.TimesheetSummary;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class MysqlTimesheetStore implements TimesheetStore {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MysqlTimesheetStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public TimesheetSummary findOrCreateCurrent(UUID tenantId, UUID userId, String cadence,
                                                  LocalDate today, Instant now) {
        return findOrCreateForDate(tenantId, userId, cadence, today, now);
    }

    @Override
    public void ensurePeriodForDate(UUID tenantId, UUID userId, String cadence, LocalDate date, Instant now) {
        findOrCreateForDate(tenantId, userId, cadence, date, now);
    }

    private TimesheetSummary findOrCreateForDate(UUID tenantId, UUID userId, String cadence,
                                                  LocalDate today, Instant now) {
        Period period = Period.forDate(today, cadence);
        jdbcTemplate.update("""
                INSERT IGNORE INTO timesheet_period
                    (id, tenant_id, user_id, period_start, period_end, cadence, status,
                     decision_note, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'OPEN', '', ?, ?)
                """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), uuidBytes(userId),
                Date.valueOf(period.start()), Date.valueOf(period.end()), period.cadence(),
                Timestamp.from(now), Timestamp.from(now));
        return findByNaturalKey(tenantId, userId, period);
    }

    @Override
    public TimesheetSummary submit(UUID tenantId, UUID userId, UUID timesheetId,
                                   boolean autoApprove, Instant now) {
        TimesheetSummary before = findById(tenantId, timesheetId);
        if (!before.userId().equals(userId)) throw new IllegalArgumentException("timesheet does not belong to the current user");
        if (!(before.status().equals(TimesheetStatus.OPEN.name())
                || before.status().equals(TimesheetStatus.REOPENED.name())
                || before.status().equals(TimesheetStatus.REJECTED.name()))) {
            throw new IllegalArgumentException("timesheet cannot be submitted from status " + before.status());
        }
        String status = autoApprove ? TimesheetStatus.APPROVED.name() : TimesheetStatus.SUBMITTED.name();
        int updated = jdbcTemplate.update("""
                UPDATE timesheet_period
                SET status = ?, submitted_at = ?, submitted_by = ?,
                    decided_at = ?, decided_by = NULL, decision_note = '', updated_at = ?
                WHERE tenant_id = ? AND id = ? AND user_id = ?
                  AND status IN ('OPEN', 'REOPENED', 'REJECTED')
                """, status, Timestamp.from(now), uuidBytes(userId),
                autoApprove ? Timestamp.from(now) : null, Timestamp.from(now),
                uuidBytes(tenantId), uuidBytes(timesheetId), uuidBytes(userId));
        if (updated == 0) throw new IllegalArgumentException("timesheet was changed or is no longer submittable");
        if (autoApprove) {
            approveEntries(tenantId, before, userId, now, "Auto-approved by company time policy");
        }
        publishEvent(tenantId, userId, timesheetId,
                autoApprove ? "TIMESHEET_AUTO_APPROVED" : "TIMESHEET_SUBMITTED", now,
                Map.of("timesheetId", timesheetId.toString(), "userId", userId.toString(),
                        "periodStart", before.periodStart().toString(), "periodEnd", before.periodEnd().toString()));
        return findById(tenantId, timesheetId);
    }

    @Override
    public List<TimesheetSummary> findPending(UUID tenantId) {
        return jdbcTemplate.query(summarySql() + " WHERE ts.tenant_id = ? AND ts.status = 'SUBMITTED' ORDER BY ts.period_end ASC, ts.id ASC LIMIT 500",
                this::map, uuidBytes(tenantId));
    }

    @Override
    public TimesheetSummary decide(UUID tenantId, UUID timesheetId, UUID approverId,
                                   boolean approved, String note, Instant now) {
        TimesheetSummary before = findById(tenantId, timesheetId);
        if (!TimesheetStatus.SUBMITTED.name().equals(before.status())) {
            throw new IllegalArgumentException("timesheet is not awaiting approval");
        }
        String status = approved ? TimesheetStatus.APPROVED.name() : TimesheetStatus.REJECTED.name();
        int updated = jdbcTemplate.update("""
                UPDATE timesheet_period
                SET status = ?, decided_at = ?, decided_by = ?, decision_note = ?, updated_at = ?
                WHERE tenant_id = ? AND id = ? AND status = 'SUBMITTED'
                """, status, Timestamp.from(now), uuidBytes(approverId), note, Timestamp.from(now),
                uuidBytes(tenantId), uuidBytes(timesheetId));
        if (updated == 0) throw new IllegalArgumentException("timesheet was already decided");
        if (approved) {
            approveEntries(tenantId, before, approverId, now, note);
        } else {
            jdbcTemplate.update("""
                    UPDATE time_entry SET approval_status = 'REJECTED', approved_by = ?, approved_at = ?, approval_note = ?
                    WHERE tenant_id = ? AND user_id = ? AND entry_date BETWEEN ? AND ?
                      AND approval_status IN ('PENDING', 'REJECTED')
                    """, uuidBytes(approverId), Timestamp.from(now), note, uuidBytes(tenantId), uuidBytes(before.userId()),
                    Date.valueOf(before.periodStart()), Date.valueOf(before.periodEnd()));
        }
        publishEvent(tenantId, approverId, timesheetId,
                approved ? "TIMESHEET_APPROVED" : "TIMESHEET_REJECTED", now,
                Map.of("timesheetId", timesheetId.toString(), "userId", before.userId().toString(),
                        "approved", approved, "note", note));
        return findById(tenantId, timesheetId);
    }

    @Override
    public TimesheetSummary reopen(UUID tenantId, UUID timesheetId, UUID actorId, String note, Instant now) {
        int updated = jdbcTemplate.update("""
                UPDATE timesheet_period
                SET status = 'REOPENED', decided_at = ?, decided_by = ?, decision_note = ?, updated_at = ?
                WHERE tenant_id = ? AND id = ? AND status = 'APPROVED'
                """, Timestamp.from(now), uuidBytes(actorId), note, Timestamp.from(now),
                uuidBytes(tenantId), uuidBytes(timesheetId));
        if (updated == 0) throw new IllegalArgumentException("only an approved timesheet can be reopened");
        publishEvent(tenantId, actorId, timesheetId, "TIMESHEET_REOPENED", now,
                Map.of("timesheetId", timesheetId.toString(), "note", note));
        return findById(tenantId, timesheetId);
    }

    @Override
    public boolean isDateLocked(UUID tenantId, UUID userId, LocalDate entryDate) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM timesheet_period
                WHERE tenant_id = ? AND user_id = ? AND status = 'APPROVED'
                  AND period_start <= ? AND period_end >= ?
                """, Integer.class, uuidBytes(tenantId), uuidBytes(userId), Date.valueOf(entryDate), Date.valueOf(entryDate));
        return count != null && count > 0;
    }

    private void approveEntries(UUID tenantId, TimesheetSummary period, UUID approverId,
                                Instant now, String note) {
        jdbcTemplate.update("""
                UPDATE time_entry SET approval_status = 'APPROVED', approved_by = ?, approved_at = ?, approval_note = ?
                WHERE tenant_id = ? AND user_id = ? AND entry_date BETWEEN ? AND ?
                  AND approval_status IN ('PENDING', 'REJECTED')
                """, uuidBytes(approverId), Timestamp.from(now), note == null ? "" : note,
                uuidBytes(tenantId), uuidBytes(period.userId()), Date.valueOf(period.periodStart()), Date.valueOf(period.periodEnd()));
    }

    private TimesheetSummary findByNaturalKey(UUID tenantId, UUID userId, Period period) {
        try {
            return jdbcTemplate.queryForObject(summarySql() + " WHERE ts.tenant_id = ? AND ts.user_id = ? AND ts.period_start = ? AND ts.period_end = ? AND ts.cadence = ?",
                    this::map, uuidBytes(tenantId), uuidBytes(userId), Date.valueOf(period.start()), Date.valueOf(period.end()), period.cadence());
        } catch (EmptyResultDataAccessException exception) {
            throw new IllegalStateException("timesheet period could not be created");
        }
    }

    private TimesheetSummary findById(UUID tenantId, UUID timesheetId) {
        try {
            return jdbcTemplate.queryForObject(summarySql() + " WHERE ts.tenant_id = ? AND ts.id = ?",
                    this::map, uuidBytes(tenantId), uuidBytes(timesheetId));
        } catch (EmptyResultDataAccessException exception) {
            throw new IllegalArgumentException("timesheet was not found");
        }
    }

    private String summarySql() {
        return """
                SELECT ts.id, ts.user_id, ts.period_start, ts.period_end, ts.cadence, ts.status,
                       COALESCE((SELECT SUM(te.minutes) FROM time_entry te WHERE te.tenant_id = ts.tenant_id AND te.user_id = ts.user_id AND te.entry_date BETWEEN ts.period_start AND ts.period_end), 0) total_minutes,
                       COALESCE((SELECT SUM(te.minutes) FROM time_entry te WHERE te.tenant_id = ts.tenant_id AND te.user_id = ts.user_id AND te.entry_date BETWEEN ts.period_start AND ts.period_end AND te.approval_status = 'PENDING'), 0) pending_minutes,
                       COALESCE((SELECT SUM(te.minutes) FROM time_entry te WHERE te.tenant_id = ts.tenant_id AND te.user_id = ts.user_id AND te.entry_date BETWEEN ts.period_start AND ts.period_end AND te.approval_status = 'APPROVED'), 0) approved_minutes,
                       COALESCE((SELECT SUM(te.minutes) FROM time_entry te WHERE te.tenant_id = ts.tenant_id AND te.user_id = ts.user_id AND te.entry_date BETWEEN ts.period_start AND ts.period_end AND te.approval_status = 'REJECTED'), 0) rejected_minutes,
                       (SELECT COUNT(*) FROM time_entry te WHERE te.tenant_id = ts.tenant_id AND te.user_id = ts.user_id AND te.entry_date BETWEEN ts.period_start AND ts.period_end) entry_count,
                       ts.submitted_at, ts.submitted_by, ts.decided_at, ts.decided_by, ts.decision_note
                FROM timesheet_period ts
                """;
    }

    private TimesheetSummary map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new TimesheetSummary(uuid(resultSet.getBytes("id")), uuid(resultSet.getBytes("user_id")),
                resultSet.getDate("period_start").toLocalDate(), resultSet.getDate("period_end").toLocalDate(),
                resultSet.getString("cadence"), resultSet.getString("status"), resultSet.getInt("total_minutes"),
                resultSet.getInt("pending_minutes"), resultSet.getInt("approved_minutes"), resultSet.getInt("rejected_minutes"),
                resultSet.getInt("entry_count"), instant(resultSet, "submitted_at"), nullableUuid(resultSet.getBytes("submitted_by")),
                instant(resultSet, "decided_at"), nullableUuid(resultSet.getBytes("decided_by")), resultSet.getString("decision_note"));
    }

    private void publishEvent(UUID tenantId, UUID actorId, UUID timesheetId, String eventType,
                              Instant occurredAt, Map<String, Object> payload) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO outbox_event
                        (event_id, tenant_id, event_type, event_version, aggregate_type, aggregate_id,
                         actor_id, correlation_id, causation_id, occurred_at, payload_json, created_at)
                    VALUES (?, ?, ?, 1, 'TIMESHEET', ?, ?, NULL, NULL, ?, ?, ?)
                    """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), eventType, uuidBytes(timesheetId),
                    uuidBytes(actorId), Timestamp.from(occurredAt), objectMapper.writeValueAsString(payload), Timestamp.from(occurredAt));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize timesheet event", exception);
        }
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record Period(LocalDate start, LocalDate end, String cadence) {
        private static Period forDate(LocalDate date, String cadence) {
            String normalized = cadence == null ? "WEEKLY" : cadence.toUpperCase();
            return switch (normalized) {
                case "DAILY" -> new Period(date, date, normalized);
                case "MONTHLY" -> new Period(date.with(TemporalAdjusters.firstDayOfMonth()), date.with(TemporalAdjusters.lastDayOfMonth()), normalized);
                default -> new Period(date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)), "WEEKLY");
            };
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
