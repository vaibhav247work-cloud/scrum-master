package com.companyos.persistence.mysql;

import com.companyos.feature.entitlement.FeatureEntitlement;
import com.companyos.feature.entitlement.FeatureEntitlementStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Repository
public class MysqlFeatureEntitlementStore implements FeatureEntitlementStore {
    private final JdbcTemplate jdbcTemplate;

    public MysqlFeatureEntitlementStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<FeatureEntitlement> findByTenantId(UUID tenantId) {
        return jdbcTemplate.query("""
                SELECT fd.feature_key, fd.display_name, fd.status,
                       COALESCE(tfe.enabled, FALSE) AS enabled
                FROM feature_definition fd
                LEFT JOIN tenant_feature_entitlement tfe
                  ON tfe.feature_key = fd.feature_key AND tfe.tenant_id = ?
                WHERE fd.status = 'ACTIVE'
                ORDER BY fd.display_name, fd.feature_key
                """, this::map, uuidBytes(tenantId));
    }

    @Override
    public boolean isEnabled(UUID tenantId, String featureKey) {
        Integer enabled = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM tenant_feature_entitlement
                WHERE tenant_id = ? AND feature_key = ? AND enabled = TRUE
                """, Integer.class, uuidBytes(tenantId), featureKey);
        return enabled != null && enabled > 0;
    }

    @Override
    public void setEnabled(UUID tenantId, String featureKey, boolean enabled, Instant updatedAt) {
        jdbcTemplate.update("""
                INSERT INTO tenant_feature_entitlement
                    (tenant_id, feature_key, enabled, configuration_json, updated_at)
                VALUES (?, ?, ?, NULL, ?)
                ON DUPLICATE KEY UPDATE enabled = VALUES(enabled), updated_at = VALUES(updated_at)
                """, uuidBytes(tenantId), featureKey, enabled, Timestamp.from(updatedAt));
    }

    private FeatureEntitlement map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new FeatureEntitlement(
                resultSet.getString("feature_key"), resultSet.getString("display_name"),
                resultSet.getBoolean("enabled"), resultSet.getString("status"));
    }

    private static byte[] uuidBytes(UUID value) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(value.getMostSignificantBits());
        buffer.putLong(value.getLeastSignificantBits());
        return buffer.array();
    }
}
