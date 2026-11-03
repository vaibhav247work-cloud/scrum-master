package com.companyos.persistence.mysql;

import com.companyos.identity.access.TenantRoleSummary;
import com.companyos.identity.access.TenantUserAdministrationStore;
import com.companyos.identity.access.TenantUserInvitationData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public class MysqlTenantUserAdministrationStore implements TenantUserAdministrationStore {
    private final JdbcTemplate jdbcTemplate;

    public MysqlTenantUserAdministrationStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<TenantRoleSummary> findRoles(UUID tenantId) {
        return jdbcTemplate.query("""
                SELECT id, role_key, display_name, system_role
                FROM tenant_role
                WHERE tenant_id = ? AND status = 'ACTIVE'
                ORDER BY display_name, id
                """, this::mapRole, uuidBytes(tenantId));
    }

    @Override
    public void createInvitation(TenantUserInvitationData data) {
        Timestamp now = Timestamp.from(data.createdAt());
        jdbcTemplate.update("""
                INSERT INTO tenant_user
                    (id, tenant_id, email, display_name, password_hash, status, activated_at,
                     offboarded_at, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, NULL, 'INVITED', NULL, NULL, ?, ?, 0)
                """, uuidBytes(data.userId()), uuidBytes(data.tenantId()), data.email(), data.displayName(), now, now);

        List<byte[]> roleIds = roleIds(data.tenantId(), data.roleKeys());
        if (roleIds.size() != data.roleKeys().size()) {
            throw new IllegalArgumentException("One or more requested roles do not exist");
        }
        for (byte[] roleId : roleIds) {
            jdbcTemplate.update("""
                    INSERT INTO tenant_user_role (tenant_id, user_id, role_id, assigned_at)
                    SELECT ?, ?, id, ? FROM tenant_role WHERE id = ? AND tenant_id = ? AND status = 'ACTIVE'
                    """, uuidBytes(data.tenantId()), uuidBytes(data.userId()), now,
                    roleId, uuidBytes(data.tenantId()));
        }

        jdbcTemplate.update("""
                INSERT INTO tenant_user_invitation
                    (id, tenant_id, user_id, email, token_hash, expires_at, consumed_at, created_by, created_at)
                VALUES (?, ?, ?, ?, ?, ?, NULL, ?, ?)
                """, uuidBytes(data.invitationId()), uuidBytes(data.tenantId()), uuidBytes(data.userId()),
                data.email(), data.tokenHash(), Timestamp.from(data.expiresAt()), uuidBytes(data.createdBy()), now);

        String payload = "{\"invitationId\":\"" + data.invitationId()
                + "\",\"userId\":\"" + data.userId()
                + "\",\"email\":\"" + jsonEscape(data.email()) + "\"}";
        jdbcTemplate.update("""
                INSERT INTO audit_event
                    (id, tenant_id, actor_id, event_type, aggregate_type, aggregate_id, correlation_id, occurred_at, payload_json)
                VALUES (?, ?, ?, 'USER_INVITED', 'TENANT_USER', ?, NULL, ?, CAST(? AS JSON))
                """, uuidBytes(UUID.randomUUID()), uuidBytes(data.tenantId()), uuidBytes(data.createdBy()),
                uuidBytes(data.userId()), now, payload);
        jdbcTemplate.update("""
                INSERT INTO outbox_event
                    (event_id, tenant_id, event_type, event_version, aggregate_type, aggregate_id, actor_id,
                     correlation_id, causation_id, occurred_at, payload_json, created_at)
                VALUES (?, ?, 'USER_INVITED', 1, 'TENANT_USER', ?, ?, NULL, NULL, ?, CAST(? AS JSON), ?)
                """, uuidBytes(UUID.randomUUID()), uuidBytes(data.tenantId()), uuidBytes(data.userId()),
                uuidBytes(data.createdBy()), now, payload, now);
    }

    @Override
    public boolean activateInvitation(UUID invitationId, String tokenHash, String passwordHash, Instant activatedAt) {
        int updated = jdbcTemplate.update("""
                UPDATE tenant_user u
                JOIN tenant_user_invitation i ON i.user_id = u.id AND i.tenant_id = u.tenant_id
                SET u.password_hash = ?, u.status = 'ACTIVE', u.activated_at = ?, u.updated_at = ?, u.version = u.version + 1
                WHERE i.id = ? AND i.token_hash = ? AND i.consumed_at IS NULL
                  AND i.expires_at > CURRENT_TIMESTAMP(6) AND u.status = 'INVITED'
                """, passwordHash, Timestamp.from(activatedAt), Timestamp.from(activatedAt),
                uuidBytes(invitationId), tokenHash);
        if (updated != 1) {
            return false;
        }
        jdbcTemplate.update("""
                UPDATE tenant_user_invitation
                SET consumed_at = ?
                WHERE id = ? AND consumed_at IS NULL
                """, Timestamp.from(activatedAt), uuidBytes(invitationId));
        return true;
    }

    @Override
    public void replaceRoles(UUID tenantId, UUID userId, Set<String> roleKeys) {
        List<byte[]> roleIds = roleIds(tenantId, roleKeys);
        if (roleIds.size() != roleKeys.size()) {
            throw new IllegalArgumentException("One or more requested roles do not exist");
        }
        jdbcTemplate.update("DELETE FROM tenant_user_role WHERE tenant_id = ? AND user_id = ?",
                uuidBytes(tenantId), uuidBytes(userId));
        for (byte[] roleId : roleIds) {
            jdbcTemplate.update("""
                    INSERT INTO tenant_user_role (tenant_id, user_id, role_id, assigned_at)
                    VALUES (?, ?, ?, CURRENT_TIMESTAMP(6))
                    """, uuidBytes(tenantId), uuidBytes(userId), roleId);
        }
    }

    @Override
    public void updateStatus(UUID tenantId, UUID userId, String status, Instant offboardedAt) {
        int updated = jdbcTemplate.update("""
                UPDATE tenant_user
                SET status = ?, offboarded_at = ?, updated_at = ?, version = version + 1
                WHERE tenant_id = ? AND id = ?
                """, status, offboardedAt == null ? null : Timestamp.from(offboardedAt),
                Timestamp.from(Instant.now()), uuidBytes(tenantId), uuidBytes(userId));
        if (updated != 1) {
            throw new IllegalArgumentException("Tenant user was not found");
        }
    }

    private List<byte[]> roleIds(UUID tenantId, Set<String> roleKeys) {
        if (roleKeys.isEmpty()) return List.of();
        String placeholders = String.join(",", roleKeys.stream().map(value -> "?").toList());
        List<Object> args = new ArrayList<>();
        args.add(uuidBytes(tenantId));
        args.addAll(roleKeys);
        args.add(uuidBytes(tenantId));
        return jdbcTemplate.query("SELECT id FROM tenant_role WHERE tenant_id = ? AND role_key IN (" + placeholders + ") AND tenant_id = ? AND status = 'ACTIVE'",
                resultSet -> {
                    List<byte[]> ids = new ArrayList<>();
                    while (resultSet.next()) ids.add(resultSet.getBytes("id"));
                    return ids;
                }, args.toArray());
    }

    private TenantRoleSummary mapRole(ResultSet resultSet, int rowNumber) throws SQLException {
        return new TenantRoleSummary(uuid(resultSet.getBytes("id")), resultSet.getString("role_key"),
                resultSet.getString("display_name"), resultSet.getBoolean("system_role"));
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
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }
}
