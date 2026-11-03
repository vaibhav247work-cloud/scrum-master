package com.companyos.eventdelivery;

import com.companyos.notification.OutboxNotificationConsumer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "outbox.notification.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxNotificationDispatcher {
    private static final int MAX_ATTEMPTS = 5;
    private static final long PROCESSING_LEASE_SECONDS = 300;

    private final JdbcTemplate jdbcTemplate;
    private final List<OutboxNotificationConsumer> consumers;

    public OutboxNotificationDispatcher(JdbcTemplate jdbcTemplate, List<OutboxNotificationConsumer> consumers) {
        this.jdbcTemplate = jdbcTemplate;
        this.consumers = consumers;
    }

    @Scheduled(fixedDelayString = "${outbox.notification.poll-ms:5000}")
    public void dispatch() {
        consumers.forEach(this::dispatchConsumer);
    }

    private void dispatchConsumer(OutboxNotificationConsumer consumer) {
        Instant now = Instant.now();
        Instant staleBefore = now.minusSeconds(PROCESSING_LEASE_SECONDS);
        List<OutboxRecord> events = jdbcTemplate.query("""
                SELECT e.event_id, e.tenant_id, e.event_type, e.payload_json
                FROM outbox_event e
                LEFT JOIN outbox_event_delivery d
                  ON d.event_id = e.event_id AND d.consumer = ?
                WHERE e.event_type IN ('TENANT_ONBOARDED', 'USER_INVITED')
                  AND (d.id IS NULL
                       OR (d.status = 'RETRY' AND d.next_attempt_at <= ?)
                       OR (d.status = 'PROCESSING' AND d.updated_at <= ?))
                ORDER BY e.created_at, e.event_id
                LIMIT 25
                """, this::map, consumer.consumerKey(), Timestamp.from(now), Timestamp.from(staleBefore));
        events.forEach(event -> ensureAndProcess(event, consumer, now, staleBefore));
    }

    private void ensureAndProcess(OutboxRecord event, OutboxNotificationConsumer consumer,
                                  Instant now, Instant staleBefore) {
        String consumerKey = consumer.consumerKey();
        jdbcTemplate.update("""
                INSERT IGNORE INTO outbox_event_delivery
                    (id, event_id, tenant_id, consumer, status, attempt_count, next_attempt_at, processed_at, last_error, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'PENDING', 0, ?, NULL, NULL, ?, ?)
                ON DUPLICATE KEY UPDATE id = id
                """, uuidBytes(UUID.randomUUID()), uuidBytes(event.eventId()), uuidBytes(event.tenantId()),
                consumerKey, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));

        int claimed = jdbcTemplate.update("""
                UPDATE outbox_event_delivery
                SET status = 'PROCESSING', attempt_count = attempt_count + 1, updated_at = ?
                WHERE event_id = ? AND consumer = ?
                  AND ((status IN ('PENDING', 'RETRY') AND next_attempt_at <= ?)
                       OR (status = 'PROCESSING' AND updated_at <= ?))
                """, Timestamp.from(now), uuidBytes(event.eventId()), consumerKey,
                Timestamp.from(now), Timestamp.from(staleBefore));
        if (claimed != 1) return;

        Integer attempt = jdbcTemplate.queryForObject("""
                SELECT attempt_count FROM outbox_event_delivery WHERE event_id = ? AND consumer = ?
                """, Integer.class, uuidBytes(event.eventId()), consumerKey);
        int attemptCount = attempt == null ? MAX_ATTEMPTS : attempt;
        if (attemptCount > MAX_ATTEMPTS) {
            markDeadLetter(event.eventId(), consumerKey, "Processing lease exceeded maximum attempts", now);
            return;
        }

        try {
            consumer.consume(event.eventId(), event.tenantId(), event.eventType(), event.payloadJson());
            Instant completedAt = Instant.now();
            jdbcTemplate.update("""
                    UPDATE outbox_event_delivery
                    SET status = 'SUCCESS', processed_at = ?, last_error = NULL, updated_at = ?
                    WHERE event_id = ? AND consumer = ? AND status = 'PROCESSING'
                    """, Timestamp.from(completedAt), Timestamp.from(completedAt), uuidBytes(event.eventId()), consumerKey);
        } catch (Exception exception) {
            boolean deadLetter = !consumer.retryable(exception) || attemptCount >= MAX_ATTEMPTS;
            Instant nextAttempt = now.plusSeconds(Math.min(300, 1L << Math.min(8, Math.max(0, attemptCount - 1))));
            jdbcTemplate.update("""
                    UPDATE outbox_event_delivery
                    SET status = ?, next_attempt_at = ?, last_error = ?, updated_at = ?
                    WHERE event_id = ? AND consumer = ? AND status = 'PROCESSING'
                    """, deadLetter ? "DEAD_LETTER" : "RETRY", Timestamp.from(nextAttempt),
                    safeError(exception), Timestamp.from(Instant.now()), uuidBytes(event.eventId()), consumerKey);
        }
    }

    private void markDeadLetter(UUID eventId, String consumer, String reason, Instant now) {
        jdbcTemplate.update("""
                UPDATE outbox_event_delivery
                SET status = 'DEAD_LETTER', last_error = ?, updated_at = ?
                WHERE event_id = ? AND consumer = ? AND status = 'PROCESSING'
                """, reason, Timestamp.from(now), uuidBytes(eventId), consumer);
    }

    private OutboxRecord map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new OutboxRecord(uuid(resultSet.getBytes("event_id")), uuid(resultSet.getBytes("tenant_id")),
                resultSet.getString("event_type"), resultSet.getString("payload_json"));
    }

    private static String safeError(Exception exception) {
        String value = exception.getClass().getSimpleName() + ": " + String.valueOf(exception.getMessage());
        return value.length() > 2000 ? value.substring(0, 2000) : value;
    }

    private record OutboxRecord(UUID eventId, UUID tenantId, String eventType, String payloadJson) { }

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
