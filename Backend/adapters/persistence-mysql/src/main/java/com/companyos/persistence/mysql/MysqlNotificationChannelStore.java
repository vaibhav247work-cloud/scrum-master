package com.companyos.persistence.mysql;

import com.companyos.notification.NotificationChannelStore;
import com.companyos.notification.TelegramChannelConfig;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MysqlNotificationChannelStore implements NotificationChannelStore {
    private final JdbcTemplate jdbcTemplate;
    private final NotificationSecretProtector secretProtector;

    public MysqlNotificationChannelStore(JdbcTemplate jdbcTemplate, NotificationSecretProtector secretProtector) {
        this.jdbcTemplate = jdbcTemplate;
        this.secretProtector = secretProtector;
    }

    @Override
    public Optional<TelegramChannelConfig> findTelegram(UUID tenantId) {
        List<TelegramChannelConfig> result = jdbcTemplate.query("""
                SELECT tenant_id, enabled, bot_token_ciphertext, chat_id, updated_at
                FROM notification_channel_config
                WHERE tenant_id = ? AND channel_type = 'TELEGRAM'
                """, this::map, uuidBytes(tenantId));
        return result.stream().findFirst();
    }

    @Override
    public void saveTelegram(TelegramChannelConfig config) {
        jdbcTemplate.update("""
                INSERT INTO notification_channel_config
                    (tenant_id, channel_type, enabled, bot_token_ciphertext, chat_id, created_at, updated_at)
                VALUES (?, 'TELEGRAM', ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE enabled = VALUES(enabled),
                    bot_token_ciphertext = COALESCE(VALUES(bot_token_ciphertext), bot_token_ciphertext),
                    chat_id = VALUES(chat_id), updated_at = VALUES(updated_at)
                """, uuidBytes(config.tenantId()), config.enabled(),
                config.botToken() == null || config.botToken().isBlank() ? null : secretProtector.encrypt(config.botToken()),
                config.chatId(), Timestamp.from(config.updatedAt()), Timestamp.from(config.updatedAt()));
    }

    private TelegramChannelConfig map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new TelegramChannelConfig(uuid(resultSet.getBytes("tenant_id")), resultSet.getBoolean("enabled"),
                secretProtector.decrypt(resultSet.getString("bot_token_ciphertext")), resultSet.getString("chat_id"),
                resultSet.getTimestamp("updated_at").toInstant());
    }

    private static byte[] uuidBytes(UUID value) { ByteBuffer buffer = ByteBuffer.allocate(16); buffer.putLong(value.getMostSignificantBits()); buffer.putLong(value.getLeastSignificantBits()); return buffer.array(); }
    private static UUID uuid(byte[] bytes) { ByteBuffer buffer = ByteBuffer.wrap(bytes); return new UUID(buffer.getLong(), buffer.getLong()); }
}
