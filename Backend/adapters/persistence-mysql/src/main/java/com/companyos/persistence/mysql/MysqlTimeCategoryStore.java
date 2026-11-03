package com.companyos.persistence.mysql;

import com.companyos.time.TimeCategory;
import com.companyos.time.TimeCategoryStore;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class MysqlTimeCategoryStore implements TimeCategoryStore {
    private final JdbcTemplate jdbcTemplate;

    public MysqlTimeCategoryStore(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Override
    public List<TimeCategory> find(UUID tenantId) {
        return jdbcTemplate.query("""
                SELECT id, category_key, name, enabled, created_at, updated_at
                FROM tenant_time_category WHERE tenant_id = ?
                ORDER BY enabled DESC, name, id
                """, this::map, uuidBytes(tenantId));
    }

    @Override
    public boolean hasConfiguredCategories(UUID tenantId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tenant_time_category WHERE tenant_id = ?",
                Integer.class, uuidBytes(tenantId));
        return count != null && count > 0;
    }

    @Override
    public boolean isEnabled(UUID tenantId, String categoryKey) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tenant_time_category WHERE tenant_id = ? AND category_key = ? AND enabled = TRUE",
                Integer.class, uuidBytes(tenantId), categoryKey);
        return count != null && count > 0;
    }

    @Override
    public TimeCategory create(UUID tenantId, String categoryKey, String name, Instant now) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO tenant_time_category (id, tenant_id, category_key, name, enabled, created_at, updated_at)
                VALUES (?, ?, ?, ?, TRUE, ?, ?)
                """, uuidBytes(id), uuidBytes(tenantId), categoryKey, name, Timestamp.from(now), Timestamp.from(now));
        return new TimeCategory(id, categoryKey, name, true, now, now);
    }

    @Override
    public TimeCategory update(UUID tenantId, UUID categoryId, String name, boolean enabled, Instant now) {
        int updated = jdbcTemplate.update("""
                UPDATE tenant_time_category SET name = ?, enabled = ?, updated_at = ?
                WHERE tenant_id = ? AND id = ?
                """, name, enabled, Timestamp.from(now), uuidBytes(tenantId), uuidBytes(categoryId));
        if (updated == 0) throw new IllegalArgumentException("time category was not found");
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT id, category_key, name, enabled, created_at, updated_at
                    FROM tenant_time_category WHERE tenant_id = ? AND id = ?
                    """, this::map, uuidBytes(tenantId), uuidBytes(categoryId));
        } catch (EmptyResultDataAccessException exception) {
            throw new IllegalArgumentException("time category was not found", exception);
        }
    }

    private TimeCategory map(ResultSet rs, int rowNumber) throws SQLException {
        return new TimeCategory(uuid(rs.getBytes("id")), rs.getString("category_key"), rs.getString("name"),
                rs.getBoolean("enabled"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private static byte[] uuidBytes(UUID value) { ByteBuffer buffer = ByteBuffer.allocate(16); buffer.putLong(value.getMostSignificantBits()).putLong(value.getLeastSignificantBits()); return buffer.array(); }
    private static UUID uuid(byte[] bytes) { ByteBuffer buffer = ByteBuffer.wrap(bytes); return new UUID(buffer.getLong(), buffer.getLong()); }
}
