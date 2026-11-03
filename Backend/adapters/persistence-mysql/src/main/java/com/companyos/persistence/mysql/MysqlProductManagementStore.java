package com.companyos.persistence.mysql;

import com.companyos.product.CreateProductCommand;
import com.companyos.product.ProductManagementStore;
import com.companyos.product.ProductMemberSummary;
import com.companyos.product.ProductSummary;
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
public class MysqlProductManagementStore implements ProductManagementStore {
    private final JdbcTemplate jdbc;

    public MysqlProductManagementStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public List<ProductSummary> findAll(UUID tenantId) {
        return jdbc.query("""
                SELECT p.id, p.tenant_id, p.product_key, p.name, p.description, p.status, p.created_at, p.updated_at,
                       (SELECT COUNT(*) FROM tenant_product_member m WHERE m.tenant_id = p.tenant_id AND m.product_id = p.id AND m.access_status = 'ACTIVE') AS member_count
                FROM tenant_product p
                WHERE p.tenant_id = ? AND p.status = 'ACTIVE'
                ORDER BY p.name, p.id
                """, this::mapProduct, uuidBytes(tenantId));
    }

    @Override
    public ProductSummary create(UUID tenantId, UUID actorId, CreateProductCommand command, Instant now) {
        UUID productId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tenant_product (id, tenant_id, product_key, name, description, status, created_by, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                """, uuidBytes(productId), uuidBytes(tenantId), command.productKey(), command.name(), command.description(),
                uuidBytes(actorId), Timestamp.from(now), Timestamp.from(now));
        replaceFeatures(tenantId, productId, command.featureKeys());
        return findProduct(tenantId, productId);
    }

    @Override
    public ProductSummary updateFeatures(UUID tenantId, UUID productId, Set<String> featureKeys, Instant now) {
        int changed = jdbc.update("UPDATE tenant_product SET updated_at = ? WHERE tenant_id = ? AND id = ? AND status = 'ACTIVE'",
                Timestamp.from(now), uuidBytes(tenantId), uuidBytes(productId));
        if (changed != 1) throw new IllegalArgumentException("product was not found");
        replaceFeatures(tenantId, productId, featureKeys);
        return findProduct(tenantId, productId);
    }

    @Override
    public List<ProductMemberSummary> findMembers(UUID tenantId, UUID productId) {
        return jdbc.query("""
                SELECT m.product_id, m.user_id, u.display_name, u.email, m.access_status, m.product_role, m.updated_at
                FROM tenant_product_member m
                JOIN tenant_user u ON u.tenant_id = m.tenant_id AND u.id = m.user_id
                WHERE m.tenant_id = ? AND m.product_id = ?
                ORDER BY u.display_name, u.id
                """, this::mapMember, uuidBytes(tenantId), uuidBytes(productId));
    }

    @Override
    public ProductMemberSummary setMember(UUID tenantId, UUID productId, UUID userId, String accessStatus,
                                          String productRole, UUID actorId, Instant now) {
        int changed = jdbc.update("""
                INSERT INTO tenant_product_member (tenant_id, product_id, user_id, access_status, product_role, created_by, created_at, updated_at)
                SELECT ?, p.id, u.id, ?, ?, ?, ?, ?
                FROM tenant_product p JOIN tenant_user u ON u.tenant_id = ? AND u.id = ?
                WHERE p.tenant_id = ? AND p.id = ? AND p.status = 'ACTIVE'
                ON DUPLICATE KEY UPDATE access_status = VALUES(access_status), product_role = VALUES(product_role), updated_at = VALUES(updated_at)
                """, uuidBytes(tenantId), accessStatus, productRole, uuidBytes(actorId), Timestamp.from(now), Timestamp.from(now),
                uuidBytes(tenantId), uuidBytes(userId), uuidBytes(tenantId), uuidBytes(productId));
        if (changed == 0) throw new IllegalArgumentException("product or user was not found");
        return jdbc.queryForObject("""
                SELECT m.product_id, m.user_id, u.display_name, u.email, m.access_status, m.product_role, m.updated_at
                FROM tenant_product_member m JOIN tenant_user u ON u.tenant_id = m.tenant_id AND u.id = m.user_id
                WHERE m.tenant_id = ? AND m.product_id = ? AND m.user_id = ?
                """, this::mapMember, uuidBytes(tenantId), uuidBytes(productId), uuidBytes(userId));
    }

    private ProductSummary findProduct(UUID tenantId, UUID productId) {
        return jdbc.queryForObject("""
                SELECT p.id, p.tenant_id, p.product_key, p.name, p.description, p.status, p.created_at, p.updated_at,
                       (SELECT COUNT(*) FROM tenant_product_member m WHERE m.tenant_id = p.tenant_id AND m.product_id = p.id AND m.access_status = 'ACTIVE') AS member_count
                FROM tenant_product p WHERE p.tenant_id = ? AND p.id = ?
                """, this::mapProduct, uuidBytes(tenantId), uuidBytes(productId));
    }

    private void replaceFeatures(UUID tenantId, UUID productId, Set<String> featureKeys) {
        jdbc.update("DELETE FROM tenant_product_feature WHERE tenant_id = ? AND product_id = ?", uuidBytes(tenantId), uuidBytes(productId));
        for (String featureKey : featureKeys) {
            jdbc.update("INSERT INTO tenant_product_feature (tenant_id, product_id, feature_key) VALUES (?, ?, ?)",
                    uuidBytes(tenantId), uuidBytes(productId), featureKey);
        }
    }

    private ProductSummary mapProduct(ResultSet rs, int row) throws SQLException {
        UUID id = uuid(rs.getBytes("id"));
        List<String> features = jdbc.queryForList("SELECT feature_key FROM tenant_product_feature WHERE tenant_id = ? AND product_id = ? ORDER BY feature_key",
                String.class, rs.getBytes("tenant_id"), rs.getBytes("id"));
        return new ProductSummary(id, rs.getString("product_key"), rs.getString("name"), rs.getString("description"), rs.getString("status"),
                features, rs.getInt("member_count"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private ProductMemberSummary mapMember(ResultSet rs, int row) throws SQLException {
        return new ProductMemberSummary(uuid(rs.getBytes("product_id")), uuid(rs.getBytes("user_id")), rs.getString("display_name"),
                rs.getString("email"), rs.getString("access_status"), rs.getString("product_role"), rs.getTimestamp("updated_at").toInstant());
    }

    private static byte[] uuidBytes(UUID value) { if (value == null) return null; ByteBuffer b = ByteBuffer.allocate(16); b.putLong(value.getMostSignificantBits()); b.putLong(value.getLeastSignificantBits()); return b.array(); }
    private static UUID uuid(byte[] value) { ByteBuffer b = ByteBuffer.wrap(value); return new UUID(b.getLong(), b.getLong()); }
}
