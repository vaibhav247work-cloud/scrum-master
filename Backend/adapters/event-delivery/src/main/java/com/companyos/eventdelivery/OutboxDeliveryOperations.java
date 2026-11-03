package com.companyos.eventdelivery;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class OutboxDeliveryOperations {
    private static final Set<String> ALLOWED_STATUSES = Set.of("PENDING", "PROCESSING", "RETRY", "SUCCESS", "DEAD_LETTER");
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final JdbcTemplate jdbcTemplate;

    public OutboxDeliveryOperations(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<OutboxDeliverySummary> list(UUID tenantId, String requestedStatus, int requestedLimit) {
        String status = normalizeStatus(requestedStatus);
        int limit = requestedLimit <= 0 ? DEFAULT_LIMIT : Math.min(requestedLimit, MAX_LIMIT);
        return jdbcTemplate.query("""
                SELECT d.id, d.event_id, e.event_type, d.consumer, d.status, d.attempt_count,
                       d.next_attempt_at, d.processed_at, d.last_error, d.created_at, d.updated_at
                FROM outbox_event_delivery d
                JOIN outbox_event e ON e.event_id = d.event_id AND e.tenant_id = d.tenant_id
                WHERE d.tenant_id = ? AND d.status = ?
                ORDER BY d.updated_at DESC, d.id DESC
                LIMIT ?
                """, this::map, uuidBytes(tenantId), status, limit);
    }

    @Transactional
    public boolean replay(UUID tenantId, UUID deliveryId, UUID actorId) {
        List<DeliveryTarget> targets = jdbcTemplate.query("""
                SELECT event_id, consumer
                FROM outbox_event_delivery
                WHERE id = ? AND tenant_id = ? AND status = 'DEAD_LETTER'
                """, (resultSet, rowNumber) -> new DeliveryTarget(
                        uuid(resultSet.getBytes("event_id")), resultSet.getString("consumer")),
                uuidBytes(deliveryId), uuidBytes(tenantId));
        if (targets.isEmpty()) return false;

        DeliveryTarget target = targets.getFirst();
        Instant now = Instant.now();
        int updated = jdbcTemplate.update("""
                UPDATE outbox_event_delivery
                SET status = 'RETRY', attempt_count = 0, next_attempt_at = ?, processed_at = NULL,
                    last_error = NULL, updated_at = ?
                WHERE id = ? AND tenant_id = ? AND status = 'DEAD_LETTER'
                """, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now),
                uuidBytes(deliveryId), uuidBytes(tenantId));
        if (updated != 1) return false;

        String payload = "{\"deliveryId\":\"" + deliveryId + "\",\"eventId\":\""
                + target.eventId() + "\",\"consumer\":\"" + jsonEscape(target.consumer()) + "\"}";
        jdbcTemplate.update("""
                INSERT INTO audit_event
                    (id, tenant_id, actor_id, event_type, aggregate_type, aggregate_id,
                     correlation_id, occurred_at, payload_json)
                VALUES (?, ?, ?, 'OUTBOX_DELIVERY_REPLAYED', 'OUTBOX_DELIVERY', ?, NULL, ?, CAST(? AS JSON))
                """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), uuidBytes(actorId), uuidBytes(deliveryId),
                java.sql.Timestamp.from(now), payload);
        return true;
    }

    private static String normalizeStatus(String requestedStatus) {
        String status = requestedStatus == null || requestedStatus.isBlank()
                ? "DEAD_LETTER" : requestedStatus.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Unsupported outbox delivery status");
        }
        return status;
    }

    private OutboxDeliverySummary map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new OutboxDeliverySummary(
                uuid(resultSet.getBytes("id")), uuid(resultSet.getBytes("event_id")),
                resultSet.getString("event_type"), resultSet.getString("consumer"),
                resultSet.getString("status"), resultSet.getInt("attempt_count"),
                resultSet.getTimestamp("next_attempt_at").toInstant(),
                instant(resultSet.getTimestamp("processed_at")), resultSet.getString("last_error"),
                resultSet.getTimestamp("created_at").toInstant(), resultSet.getTimestamp("updated_at").toInstant());
    }

    private static Instant instant(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
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

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record DeliveryTarget(UUID eventId, String consumer) { }
}
