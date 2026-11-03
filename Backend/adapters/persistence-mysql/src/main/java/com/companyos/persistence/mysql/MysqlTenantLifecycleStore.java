package com.companyos.persistence.mysql;

import com.companyos.tenantmanagement.api.TenantLifecycleStore;
import com.companyos.tenantmanagement.api.TenantLifecycleSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class MysqlTenantLifecycleStore implements TenantLifecycleStore {
    private static final List<String> SENSITIVE_COLUMNS = List.of(
            "password", "password_hash", "token", "token_hash", "secret", "secret_ciphertext");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public MysqlTenantLifecycleStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public TenantLifecycleSummary find(UUID tenantId) {
        return jdbc.queryForObject("""
                SELECT id, tenant_key, display_name, status, suspended_at, deletion_scheduled_at, updated_at
                FROM tenant WHERE id = ?
                """, (rs, row) -> new TenantLifecycleSummary(
                uuid(rs.getBytes("id")), rs.getString("tenant_key"), rs.getString("display_name"),
                rs.getString("status"), instant(rs.getTimestamp("suspended_at")),
                instant(rs.getTimestamp("deletion_scheduled_at")), instant(rs.getTimestamp("updated_at"))),
                uuidBytes(tenantId));
    }

    @Override
    public TenantLifecycleSummary updateStatus(UUID tenantId, UUID actorId, String status, String reason) {
        TenantLifecycleSummary before = find(tenantId);
        Instant now = Instant.now();
        Timestamp suspendedAt = "SUSPENDED".equals(status) ? Timestamp.from(now) : null;
        Timestamp deletionAt = "DELETION_SCHEDULED".equals(status)
                ? Timestamp.from(com.companyos.tenantmanagement.api.TenantLifecycleService.deletionDateFromNow())
                : null;
        jdbc.update("""
                UPDATE tenant SET status = ?, suspended_at = ?, deletion_scheduled_at = ?, lifecycle_reason = ?, updated_at = ?
                WHERE id = ?
                """, status, suspendedAt, deletionAt, reason.isBlank() ? null : reason, Timestamp.from(now), uuidBytes(tenantId));
        jdbc.update("""
                INSERT INTO tenant_lifecycle_event
                    (id, tenant_id, actor_id, from_status, to_status, reason, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), nullableUuidBytes(actorId), before.status(), status,
                reason.isBlank() ? null : reason, Timestamp.from(now));
        return find(tenantId);
    }

    @Override
    public Map<String, Object> export(UUID tenantId) {
        TenantLifecycleSummary tenant = find(tenantId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exportVersion", 1);
        result.put("exportedAt", Instant.now());
        result.put("tenant", Map.of(
                "tenantId", tenant.tenantId(), "tenantKey", tenant.tenantKey(),
                "displayName", tenant.displayName(), "status", tenant.status()));

        List<String> tables = jdbc.queryForList("""
                SELECT DISTINCT c.table_name
                FROM information_schema.columns c
                WHERE c.table_schema = DATABASE() AND c.column_name = 'tenant_id'
                ORDER BY c.table_name
                """, String.class);
        Map<String, Object> data = new LinkedHashMap<>();
        for (String table : tables) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT * FROM `" + safeIdentifier(table) + "` WHERE tenant_id = ? LIMIT 10000", uuidBytes(tenantId));
            List<Map<String, Object>> sanitized = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> copy = new LinkedHashMap<>();
                row.forEach((key, value) -> {
                    if (!isSensitive(key)) copy.put(key, exportValue(value));
                });
                sanitized.add(copy);
            }
            data.put(table, sanitized);
        }
        result.put("data", data);
        result.put("limits", Map.of("maxRowsPerTable", 10000,
                "sensitiveCredentialFieldsOmitted", true));
        return result;
    }

    private static boolean isSensitive(String column) {
        String normalized = column.toLowerCase();
        return SENSITIVE_COLUMNS.stream().anyMatch(normalized::contains);
    }

    private Object exportValue(Object value) {
        if (value instanceof byte[] bytes) return Base64.getEncoder().encodeToString(bytes);
        return value;
    }

    private static String safeIdentifier(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_]+")) {
            throw new IllegalStateException("Unexpected database identifier");
        }
        return value;
    }

    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
    private static byte[] nullableUuidBytes(UUID value) { return value == null ? null : uuidBytes(value); }
    private static byte[] uuidBytes(UUID value) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(value.getMostSignificantBits()).putLong(value.getLeastSignificantBits());
        return buffer.array();
    }
    private static UUID uuid(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
