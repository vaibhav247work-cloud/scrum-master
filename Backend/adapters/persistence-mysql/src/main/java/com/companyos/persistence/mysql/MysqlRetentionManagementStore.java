package com.companyos.persistence.mysql;

import com.companyos.retention.RetentionCleanupSummary;
import com.companyos.retention.RetentionHoldSummary;
import com.companyos.retention.RetentionManagementStore;
import com.companyos.retention.RetentionPolicyCommand;
import com.companyos.retention.RetentionPolicySummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Repository
public class MysqlRetentionManagementStore implements RetentionManagementStore {
    private static final int BATCH_SIZE = 1000;
    private final JdbcTemplate jdbc;

    public MysqlRetentionManagementStore(JdbcTemplate jdbc, ObjectMapper ignored) {
        this.jdbc = jdbc;
    }

    @Override
    public RetentionPolicySummary findPolicy(UUID tenantId) {
        return jdbc.query("""
                SELECT enabled, operation_mode, activity_retention_days, notification_retention_days,
                       delivery_retention_days, audit_retention_days, policy_version, updated_at
                FROM tenant_retention_policy WHERE tenant_id = ?
                """, rs -> rs.next() ? new RetentionPolicySummary(tenantId, rs.getBoolean("enabled"),
                rs.getString("operation_mode"), rs.getInt("activity_retention_days"),
                rs.getInt("notification_retention_days"), rs.getInt("delivery_retention_days"),
                rs.getInt("audit_retention_days"), rs.getInt("policy_version"), rs.getTimestamp("updated_at").toInstant())
                : defaults(tenantId), uuidBytes(tenantId));
    }

    @Override
    public RetentionPolicySummary savePolicy(UUID tenantId, UUID actorId, RetentionPolicyCommand command) {
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO tenant_retention_policy
                    (tenant_id, enabled, operation_mode, activity_retention_days, notification_retention_days,
                     delivery_retention_days, audit_retention_days, policy_version, updated_by, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, ?)
                ON DUPLICATE KEY UPDATE enabled = VALUES(enabled), operation_mode = VALUES(operation_mode),
                    activity_retention_days = VALUES(activity_retention_days), notification_retention_days = VALUES(notification_retention_days),
                    delivery_retention_days = VALUES(delivery_retention_days), audit_retention_days = VALUES(audit_retention_days),
                    policy_version = policy_version + 1, updated_by = VALUES(updated_by), updated_at = VALUES(updated_at)
                """, uuidBytes(tenantId), command.enabled(), command.operationMode(), command.activityRetentionDays(),
                command.notificationRetentionDays(), command.deliveryRetentionDays(), command.auditRetentionDays(),
                uuidBytes(actorId), Timestamp.from(now));
        audit(tenantId, actorId, UUID.randomUUID(), "RETENTION_POLICY_UPDATED", now);
        return findPolicy(tenantId);
    }

    @Override
    public RetentionCleanupSummary preview(UUID tenantId) {
        RetentionPolicySummary policy = findPolicy(tenantId);
        Instant now = Instant.now();
        Instant notificationCutoff = now.minus(policy.notificationRetentionDays(), ChronoUnit.DAYS);
        Instant deliveryCutoff = now.minus(policy.deliveryRetentionDays(), ChronoUnit.DAYS);
        Instant auditCutoff = now.minus(policy.auditRetentionDays(), ChronoUnit.DAYS);
        Instant activityCutoff = now.minus(policy.activityRetentionDays(), ChronoUnit.DAYS);
        boolean hold = hasActiveHold(tenantId);
        return new RetentionCleanupSummary(tenantId, hold ? "LEGAL_HOLD" : policy.operationMode(),
                count("SELECT COUNT(*) FROM in_app_notification WHERE tenant_id = ? AND created_at < ?", tenantId, notificationCutoff),
                count("SELECT COUNT(*) FROM outbox_event_delivery WHERE tenant_id = ? AND status = 'SUCCESS' AND processed_at IS NOT NULL AND processed_at < ?", tenantId, deliveryCutoff),
                count("SELECT COUNT(*) FROM outbox_event e WHERE e.tenant_id = ? AND e.created_at < ? AND NOT EXISTS (SELECT 1 FROM in_app_notification n WHERE n.tenant_id = e.tenant_id AND n.source_event_id = e.event_id) AND NOT EXISTS (SELECT 1 FROM outbox_event_delivery d WHERE d.tenant_id = e.tenant_id AND d.event_id = e.event_id)", tenantId, activityCutoff),
                count("SELECT COUNT(*) FROM audit_event WHERE tenant_id = ? AND occurred_at < ?", tenantId, auditCutoff),
                0, 0, 0, 0, hold, now);
    }

    @Override
    public RetentionCleanupSummary cleanup(UUID tenantId, UUID actorId) {
        RetentionPolicySummary policy = findPolicy(tenantId);
        RetentionCleanupSummary before = preview(tenantId);
        Instant started = Instant.now();
        UUID runId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO retention_cleanup_run
                    (id, tenant_id, actor_id, status, legal_hold_active, started_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, uuidBytes(runId), uuidBytes(tenantId), uuidBytes(actorId), before.legalHoldActive() ? "SKIPPED_HOLD" : "RUNNING",
                before.legalHoldActive(), Timestamp.from(started));
        if (before.legalHoldActive()) {
            return finish(tenantId, actorId, runId, before, 0, 0, 0, 0, "SKIPPED_HOLD", started);
        }
        Instant notificationCutoff = started.minus(policy.notificationRetentionDays(), ChronoUnit.DAYS);
        Instant deliveryCutoff = started.minus(policy.deliveryRetentionDays(), ChronoUnit.DAYS);
        Instant auditCutoff = started.minus(policy.auditRetentionDays(), ChronoUnit.DAYS);
        Instant activityCutoff = started.minus(policy.activityRetentionDays(), ChronoUnit.DAYS);
        int notifications = jdbc.update("DELETE FROM in_app_notification WHERE tenant_id = ? AND created_at < ? LIMIT " + BATCH_SIZE,
                uuidBytes(tenantId), Timestamp.from(notificationCutoff));
        int deliveries = jdbc.update("DELETE FROM outbox_event_delivery WHERE tenant_id = ? AND status = 'SUCCESS' AND processed_at IS NOT NULL AND processed_at < ? LIMIT " + BATCH_SIZE,
                uuidBytes(tenantId), Timestamp.from(deliveryCutoff));
        int outbox = jdbc.update("""
                DELETE FROM outbox_event
                WHERE tenant_id = ? AND created_at < ?
                  AND NOT EXISTS (SELECT 1 FROM in_app_notification n WHERE n.tenant_id = outbox_event.tenant_id AND n.source_event_id = outbox_event.event_id)
                  AND NOT EXISTS (SELECT 1 FROM outbox_event_delivery d WHERE d.tenant_id = outbox_event.tenant_id AND d.event_id = outbox_event.event_id)
                LIMIT """ + BATCH_SIZE, uuidBytes(tenantId), Timestamp.from(activityCutoff));
        int auditEvents = jdbc.update("DELETE FROM audit_event WHERE tenant_id = ? AND occurred_at < ? LIMIT " + BATCH_SIZE,
                uuidBytes(tenantId), Timestamp.from(auditCutoff));
        return finish(tenantId, actorId, runId, before, notifications, deliveries, outbox, auditEvents, "COMPLETED", started);
    }

    private RetentionCleanupSummary finish(UUID tenantId, UUID actorId, UUID runId, RetentionCleanupSummary before,
                                           int notifications, int deliveries, int outbox, int auditEvents,
                                           String status, Instant started) {
        Instant completed = Instant.now();
        jdbc.update("""
                UPDATE retention_cleanup_run
                SET status = ?, notifications_removed = ?, deliveries_removed = ?, outbox_events_removed = ?,
                    audit_events_removed = ?, completed_at = ?
                WHERE tenant_id = ? AND id = ?
                """, status, notifications, deliveries, outbox, auditEvents, Timestamp.from(completed),
                uuidBytes(tenantId), uuidBytes(runId));
        audit(tenantId, actorId, runId, "RETENTION_CLEANUP_" + status, completed);
        return new RetentionCleanupSummary(tenantId, status, before.notificationsEligible(), before.deliveriesEligible(),
                before.outboxEventsEligible(), before.auditEventsEligible(), notifications, deliveries, outbox, auditEvents,
                before.legalHoldActive(), completed);
    }

    @Override
    public List<RetentionHoldSummary> findHolds(UUID tenantId) {
        return jdbc.query("""
                SELECT id, hold_key, reason, active, created_at, released_at
                FROM tenant_retention_hold WHERE tenant_id = ? ORDER BY active DESC, created_at DESC, id DESC
                """, (rs, row) -> new RetentionHoldSummary(uuid(rs.getBytes("id")), rs.getString("hold_key"),
                rs.getString("reason"), rs.getBoolean("active"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("released_at") == null ? null : rs.getTimestamp("released_at").toInstant()), uuidBytes(tenantId));
    }

    @Override
    public RetentionHoldSummary createHold(UUID tenantId, UUID actorId, String holdKey, String reason) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO tenant_retention_hold
                    (id, tenant_id, hold_key, reason, active, created_by, created_at)
                VALUES (?, ?, ?, ?, TRUE, ?, ?)
                """, uuidBytes(id), uuidBytes(tenantId), holdKey, reason, uuidBytes(actorId), Timestamp.from(now));
        audit(tenantId, actorId, id, "RETENTION_HOLD_CREATED", now);
        return findHolds(tenantId).stream().filter(hold -> hold.id().equals(id)).findFirst().orElseThrow();
    }

    @Override
    public RetentionHoldSummary releaseHold(UUID tenantId, UUID actorId, UUID holdId) {
        int changed = jdbc.update("""
                UPDATE tenant_retention_hold SET active = FALSE, released_by = ?, released_at = CURRENT_TIMESTAMP(6)
                WHERE tenant_id = ? AND id = ? AND active = TRUE
                """, uuidBytes(actorId), uuidBytes(tenantId), uuidBytes(holdId));
        if (changed == 0) throw new IllegalArgumentException("active retention hold was not found");
        audit(tenantId, actorId, holdId, "RETENTION_HOLD_RELEASED", Instant.now());
        return findHolds(tenantId).stream().filter(hold -> hold.id().equals(holdId)).findFirst().orElseThrow();
    }

    private boolean hasActiveHold(UUID tenantId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM tenant_retention_hold WHERE tenant_id = ? AND active = TRUE)", Boolean.class, uuidBytes(tenantId)));
    }
    private int count(String sql, UUID tenantId, Instant cutoff) { return jdbc.queryForObject(sql, Integer.class, uuidBytes(tenantId), Timestamp.from(cutoff)); }
    private RetentionPolicySummary defaults(UUID tenantId) { return new RetentionPolicySummary(tenantId, false, "PREVIEW_ONLY", 120, 90, 90, 365, 1, null); }
    private void audit(UUID tenantId, UUID actorId, UUID aggregateId, String type, Instant now) {
        jdbc.update("""
                INSERT INTO audit_event (id, tenant_id, actor_id, event_type, aggregate_type, aggregate_id, occurred_at, payload_json)
                VALUES (?, ?, ?, ?, 'RETENTION', ?, ?, '{}')
                """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), uuidBytes(actorId), type, uuidBytes(aggregateId), Timestamp.from(now));
    }
    private static byte[] uuidBytes(UUID value) { if (value == null) return null; ByteBuffer b = ByteBuffer.allocate(16); b.putLong(value.getMostSignificantBits()).putLong(value.getLeastSignificantBits()); return b.array(); }
    private static UUID uuid(byte[] value) { ByteBuffer b = ByteBuffer.wrap(value); return new UUID(b.getLong(), b.getLong()); }
}
