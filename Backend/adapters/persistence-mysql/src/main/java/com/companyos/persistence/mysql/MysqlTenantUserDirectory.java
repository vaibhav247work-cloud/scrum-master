package com.companyos.persistence.mysql;

import com.companyos.identity.access.TenantUserDirectory;
import com.companyos.identity.access.TenantUserSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Repository
public class MysqlTenantUserDirectory implements TenantUserDirectory {
    private final JdbcTemplate jdbcTemplate;

    public MysqlTenantUserDirectory(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<TenantUserSummary> findByTenantId(UUID tenantId) {
        return jdbcTemplate.query("""
                SELECT id, email, display_name, status
                FROM tenant_user
                WHERE tenant_id = ?
                ORDER BY display_name, id
                """, this::map, uuidBytes(tenantId));
    }

    private TenantUserSummary map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new TenantUserSummary(
                uuid(resultSet.getBytes("id")),
                resultSet.getString("email"),
                resultSet.getString("display_name"),
                resultSet.getString("status"));
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
