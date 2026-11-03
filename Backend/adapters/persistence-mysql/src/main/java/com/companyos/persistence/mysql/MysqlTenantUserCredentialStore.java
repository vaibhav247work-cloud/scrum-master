package com.companyos.persistence.mysql;

import com.companyos.identity.access.TenantUserCredentialStore;
import com.companyos.identity.access.TenantUserCredentials;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Repository
public class MysqlTenantUserCredentialStore implements TenantUserCredentialStore {
    private final JdbcTemplate jdbcTemplate;

    public MysqlTenantUserCredentialStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<TenantUserCredentials> findByTenantKeyAndEmail(String tenantKey, String email) {
        List<CredentialRow> rows = jdbcTemplate.query("""
                SELECT t.id AS tenant_id, t.tenant_key, u.id AS user_id, u.email,
                       u.display_name, u.password_hash, u.status,
                       r.role_key, rp.permission_key
                FROM tenant t
                JOIN tenant_user u ON u.tenant_id = t.id
                LEFT JOIN tenant_user_role ur ON ur.tenant_id = u.tenant_id AND ur.user_id = u.id
                LEFT JOIN tenant_role r ON r.id = ur.role_id AND r.status = 'ACTIVE'
                LEFT JOIN tenant_role_permission rp ON rp.tenant_id = r.tenant_id AND rp.role_id = r.id
                WHERE t.tenant_key = ?
                  AND u.email = ?
                  AND t.status = 'ACTIVE'
                ORDER BY r.role_key, rp.permission_key
                """, (resultSet, rowNumber) -> new CredentialRow(
                uuid(resultSet.getBytes("tenant_id")), resultSet.getString("tenant_key"),
                uuid(resultSet.getBytes("user_id")), resultSet.getString("email"),
                resultSet.getString("display_name"), resultSet.getString("password_hash"),
                resultSet.getString("status"), resultSet.getString("role_key"),
                resultSet.getString("permission_key")), tenantKey, email);

        if (rows.isEmpty()) return Optional.empty();
        CredentialRow first = rows.get(0);
        Set<String> authorities = new HashSet<>();
        for (CredentialRow row : rows) {
            addAuthority(authorities, row.roleKey(), "ROLE_");
            addAuthority(authorities, row.permissionKey(), "PERM_");
        }
        return Optional.of(new TenantUserCredentials(
                first.tenantId(), first.userId(), first.tenantKey(), first.email(), first.displayName(),
                first.passwordHash(), first.status(), Set.copyOf(authorities)));
    }

    private record CredentialRow(UUID tenantId, String tenantKey, UUID userId, String email,
                                 String displayName, String passwordHash, String status,
                                 String roleKey, String permissionKey) { }

    private static void addAuthority(Set<String> authorities, String value, String prefix) {
        if (value != null && !value.isBlank()) authorities.add(prefix + value);
    }

    private static UUID uuid(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
