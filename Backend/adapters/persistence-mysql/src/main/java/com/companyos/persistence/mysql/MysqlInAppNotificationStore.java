package com.companyos.persistence.mysql;

import com.companyos.notification.InAppNotification;
import com.companyos.notification.InAppNotificationStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Repository
public class MysqlInAppNotificationStore implements InAppNotificationStore {
    private final JdbcTemplate jdbcTemplate;

    public MysqlInAppNotificationStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void create(InAppNotification notification) {
        jdbcTemplate.update("""
                INSERT IGNORE INTO in_app_notification
                    (id, tenant_id, recipient_user_id, source_event_id, notification_type, title, body, read_at, created_at)
                SELECT ?, ?, u.id, ?, ?, ?, ?, NULL, ?
                FROM tenant_user u
                WHERE u.tenant_id = ? AND u.id = ?
                """, uuidBytes(notification.id()), uuidBytes(notification.tenantId()),
                uuidBytes(notification.sourceEventId()), notification.notificationType(), notification.title(),
                notification.body(), Timestamp.from(notification.createdAt()), uuidBytes(notification.tenantId()),
                uuidBytes(notification.recipientUserId()));
    }

    @Override
    public List<InAppNotification> findForUser(UUID tenantId, UUID userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return jdbcTemplate.query("""
                SELECT id, tenant_id, recipient_user_id, source_event_id, notification_type, title, body, read_at, created_at
                FROM in_app_notification
                WHERE tenant_id = ? AND recipient_user_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """, this::map, uuidBytes(tenantId), uuidBytes(userId), safeLimit);
    }

    private InAppNotification map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new InAppNotification(uuid(resultSet.getBytes("id")), uuid(resultSet.getBytes("tenant_id")),
                uuid(resultSet.getBytes("recipient_user_id")), uuid(resultSet.getBytes("source_event_id")),
                resultSet.getString("notification_type"), resultSet.getString("title"),
                resultSet.getString("body"), nullableTimestamp(resultSet, "read_at"), resultSet.getTimestamp("created_at").toInstant());
    }

    @Override
    public boolean markRead(UUID tenantId, UUID userId, UUID notificationId, java.time.Instant readAt) {
        return jdbcTemplate.update("""
                UPDATE in_app_notification
                SET read_at = COALESCE(read_at, ?)
                WHERE tenant_id = ? AND recipient_user_id = ? AND id = ?
                """, Timestamp.from(readAt), uuidBytes(tenantId), uuidBytes(userId), uuidBytes(notificationId)) == 1;
    }

    private static java.time.Instant nullableTimestamp(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
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
}
