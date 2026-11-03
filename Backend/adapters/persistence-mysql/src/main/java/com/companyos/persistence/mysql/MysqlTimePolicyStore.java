package com.companyos.persistence.mysql;

import com.companyos.time.TimePolicy;
import com.companyos.time.TimePolicyStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class MysqlTimePolicyStore implements TimePolicyStore {
    private final JdbcTemplate jdbcTemplate;

    public MysqlTimePolicyStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public TimePolicy find(UUID tenantId) {
        List<TimePolicy> policies = jdbcTemplate.query("""
                SELECT enabled, expected_daily_minutes, allow_overtime, allow_weekend,
                       approval_enabled, approval_cadence, auto_approve, rounding_increment_minutes, rounding_mode
                FROM tenant_time_policy
                WHERE tenant_id = ?
                """, this::map, uuidBytes(tenantId));
        return policies.isEmpty() ? TimePolicy.defaults() : policies.get(0);
    }

    @Override
    public TimePolicy save(UUID tenantId, TimePolicy policy, Instant updatedAt) {
        jdbcTemplate.update("""
                INSERT INTO tenant_time_policy
                    (tenant_id, enabled, expected_daily_minutes, allow_overtime, allow_weekend,
                     approval_enabled, approval_cadence, auto_approve, rounding_increment_minutes, rounding_mode, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    enabled = VALUES(enabled),
                    expected_daily_minutes = VALUES(expected_daily_minutes),
                    allow_overtime = VALUES(allow_overtime),
                    allow_weekend = VALUES(allow_weekend),
                    approval_enabled = VALUES(approval_enabled),
                    approval_cadence = VALUES(approval_cadence),
                    auto_approve = VALUES(auto_approve),
                    rounding_increment_minutes = VALUES(rounding_increment_minutes),
                    rounding_mode = VALUES(rounding_mode),
                    updated_at = VALUES(updated_at)
                """, uuidBytes(tenantId), policy.enabled(), policy.expectedDailyMinutes(), policy.allowOvertime(),
                policy.allowWeekend(), policy.approvalEnabled(), policy.approvalCadence(), policy.autoApprove(),
                policy.roundingIncrementMinutes(), policy.roundingMode(),
                java.sql.Timestamp.from(updatedAt));
        return policy;
    }

    private TimePolicy map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new TimePolicy(
                resultSet.getBoolean("enabled"),
                resultSet.getInt("expected_daily_minutes"),
                resultSet.getBoolean("allow_overtime"),
                resultSet.getBoolean("allow_weekend"),
                resultSet.getBoolean("approval_enabled"),
                resultSet.getString("approval_cadence"),
                resultSet.getBoolean("auto_approve"),
                resultSet.getInt("rounding_increment_minutes"),
                resultSet.getString("rounding_mode"));
    }

    private static byte[] uuidBytes(UUID value) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(value.getMostSignificantBits());
        buffer.putLong(value.getLeastSignificantBits());
        return buffer.array();
    }
}
