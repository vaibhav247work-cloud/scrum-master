package com.companyos.persistence.mysql;

import com.companyos.reporting.ForecastPolicy;
import com.companyos.reporting.ForecastPolicyStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class MysqlForecastPolicyStore implements ForecastPolicyStore {
    private final JdbcTemplate jdbcTemplate;

    public MysqlForecastPolicyStore(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Override
    public ForecastPolicy find(UUID tenantId) {
        List<ForecastPolicy> policies = jdbcTemplate.query("""
                SELECT enabled, historical_calibration_enabled, release_at_risk_days, release_at_risk_completion_percent,
                       project_over_plan_percent
                FROM tenant_forecast_policy
                WHERE tenant_id = ?
                """, this::map, uuidBytes(tenantId));
        return policies.isEmpty() ? ForecastPolicy.defaults() : policies.get(0).validated();
    }

    @Override
    public ForecastPolicy save(UUID tenantId, UUID actorId, ForecastPolicy policy, Instant updatedAt) {
        jdbcTemplate.update("""
                INSERT INTO tenant_forecast_policy
                    (tenant_id, enabled, historical_calibration_enabled, release_at_risk_days, release_at_risk_completion_percent,
                     project_over_plan_percent, updated_by, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    enabled = VALUES(enabled),
                    historical_calibration_enabled = VALUES(historical_calibration_enabled),
                    release_at_risk_days = VALUES(release_at_risk_days),
                    release_at_risk_completion_percent = VALUES(release_at_risk_completion_percent),
                    project_over_plan_percent = VALUES(project_over_plan_percent),
                    updated_by = VALUES(updated_by),
                    updated_at = VALUES(updated_at)
                """, uuidBytes(tenantId), policy.enabled(), policy.historicalCalibrationEnabled(), policy.releaseAtRiskDays(),
                policy.releaseAtRiskCompletionPercent(), policy.projectOverPlanPercent(),
                actorId == null ? null : uuidBytes(actorId), java.sql.Timestamp.from(updatedAt));
        return policy;
    }

    private ForecastPolicy map(ResultSet rs, int rowNumber) throws SQLException {
        return new ForecastPolicy(rs.getBoolean("enabled"), rs.getBoolean("historical_calibration_enabled"), rs.getInt("release_at_risk_days"),
                rs.getInt("release_at_risk_completion_percent"), rs.getInt("project_over_plan_percent"));
    }

    private static byte[] uuidBytes(UUID value) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(value.getMostSignificantBits()).putLong(value.getLeastSignificantBits());
        return buffer.array();
    }
}
