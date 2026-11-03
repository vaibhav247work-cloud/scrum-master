package com.companyos.persistence.mysql;

import com.companyos.tenantmanagement.port.TenantOnboardingData;
import com.companyos.tenantmanagement.port.TenantOnboardingStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Repository
public class MysqlTenantOnboardingStore implements TenantOnboardingStore {
    private final JdbcTemplate jdbcTemplate;

    public MysqlTenantOnboardingStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void createTenant(TenantOnboardingData data) {
        Timestamp createdAt = Timestamp.from(data.createdAt());

        jdbcTemplate.update("""
                INSERT INTO tenant
                    (id, tenant_key, legal_name, display_name, status, default_timezone, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                """,
                uuidBytes(data.tenantId()), data.tenantKey(), data.legalName(), data.displayName(),
                data.timezone(), createdAt, createdAt);

        jdbcTemplate.update("""
                INSERT INTO tenant_user
                    (id, tenant_id, email, display_name, password_hash, status, activated_at, offboarded_at,
                     created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, NULL, ?, ?, 0)
                """,
                uuidBytes(data.ownerId()), uuidBytes(data.tenantId()), data.ownerEmail(), data.ownerDisplayName(),
                data.ownerPasswordHash(), createdAt, createdAt, createdAt);

        jdbcTemplate.update("""
                INSERT INTO tenant_role
                    (id, tenant_id, role_key, display_name, system_role, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, TRUE, 'ACTIVE', ?, ?)
                """,
                uuidBytes(data.initialRoleId()), uuidBytes(data.tenantId()), data.initialRoleKey(),
                "Company Administrator", createdAt, createdAt);

        for (String permission : data.initialRolePermissions()) {
            jdbcTemplate.update("""
                    INSERT INTO tenant_role_permission (tenant_id, role_id, permission_key)
                    VALUES (?, ?, ?)
                    """, uuidBytes(data.tenantId()), uuidBytes(data.initialRoleId()), permission);
        }

        jdbcTemplate.update("""
                INSERT INTO tenant_user_role (tenant_id, user_id, role_id, assigned_at)
                VALUES (?, ?, ?, ?)
                """, uuidBytes(data.tenantId()), uuidBytes(data.ownerId()), uuidBytes(data.initialRoleId()), createdAt);

        for (String feature : data.enabledFeatures()) {
            jdbcTemplate.update("""
                    INSERT INTO tenant_feature_entitlement
                        (tenant_id, feature_key, enabled, configuration_json, updated_at)
                    VALUES (?, ?, TRUE, NULL, ?)
                    """, uuidBytes(data.tenantId()), feature, createdAt);
        }

        UUID defaultWorkflowId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO workflow_definition
                    (id, tenant_id, workflow_key, name, methodology, status, created_by, created_at, updated_at)
                VALUES (?, ?, 'DEFAULT', 'Default delivery workflow', 'HYBRID', 'ACTIVE', ?, ?, ?)
                """, uuidBytes(defaultWorkflowId), uuidBytes(data.tenantId()), uuidBytes(data.ownerId()), createdAt, createdAt);
        Map<String, UUID> workflowStatuses = new LinkedHashMap<>();
        addWorkflowStatus(workflowStatuses, data, defaultWorkflowId, "TODO", "To do", false, 10, createdAt);
        addWorkflowStatus(workflowStatuses, data, defaultWorkflowId, "IN_PROGRESS", "In progress", false, 20, createdAt);
        addWorkflowStatus(workflowStatuses, data, defaultWorkflowId, "BLOCKED", "Blocked", false, 30, createdAt);
        addWorkflowStatus(workflowStatuses, data, defaultWorkflowId, "DONE", "Done", true, 40, createdAt);
        addWorkflowStatus(workflowStatuses, data, defaultWorkflowId, "CANCELLED", "Cancelled", true, 50, createdAt);
        for (String from : new String[]{"TODO", "IN_PROGRESS", "BLOCKED"}) {
            for (String to : new String[]{"TODO", "IN_PROGRESS", "BLOCKED", "DONE", "CANCELLED"}) {
                if (!from.equals(to)) {
                    jdbcTemplate.update("""
                            INSERT INTO workflow_transition
                                (id, tenant_id, workflow_id, from_status_id, to_status_id, created_at)
                            VALUES (?, ?, ?, ?, ?, ?)
                            """, uuidBytes(UUID.randomUUID()), uuidBytes(data.tenantId()), uuidBytes(defaultWorkflowId),
                            uuidBytes(workflowStatuses.get(from)), uuidBytes(workflowStatuses.get(to)), createdAt);
                }
            }
        }

        String payload = "{\"tenantId\":\"" + data.tenantId()
                + "\",\"ownerId\":\"" + data.ownerId()
                + "\",\"tenantKey\":\"" + jsonEscape(data.tenantKey())
                + "\",\"ownerEmail\":\"" + jsonEscape(data.ownerEmail()) + "\"}";
        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO audit_event
                    (id, tenant_id, actor_id, event_type, aggregate_type, aggregate_id, correlation_id, occurred_at, payload_json)
                VALUES (?, ?, NULL, 'TENANT_ONBOARDED', 'TENANT', ?, NULL, ?, CAST(? AS JSON))
                """,
                uuidBytes(UUID.randomUUID()), uuidBytes(data.tenantId()), uuidBytes(data.tenantId()), createdAt, payload);

        jdbcTemplate.update("""
                INSERT INTO outbox_event
                    (event_id, tenant_id, event_type, event_version, aggregate_type, aggregate_id, actor_id,
                     correlation_id, causation_id, occurred_at, payload_json, created_at)
                VALUES (?, ?, 'TENANT_ONBOARDED', 1, 'TENANT', ?, NULL, NULL, NULL, ?, CAST(? AS JSON), ?)
                """,
                uuidBytes(eventId), uuidBytes(data.tenantId()), uuidBytes(data.tenantId()), createdAt, payload, createdAt);
    }

    private void addWorkflowStatus(Map<String, UUID> statuses, TenantOnboardingData data, UUID workflowId,
                                   String key, String label, boolean terminal, int position, Timestamp createdAt) {
        UUID statusId = UUID.randomUUID();
        statuses.put(key, statusId);
        jdbcTemplate.update("""
                INSERT INTO workflow_status
                    (id, tenant_id, workflow_id, status_key, label, terminal, position, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, uuidBytes(statusId), uuidBytes(data.tenantId()), uuidBytes(workflowId), key, label, terminal, position, createdAt);
    }

    private static byte[] uuidBytes(UUID value) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(value.getMostSignificantBits());
        buffer.putLong(value.getLeastSignificantBits());
        return buffer.array();
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }
}
