INSERT INTO permission_definition (permission_key, display_name, status) VALUES
    ('integration.delivery.read', 'View outbox delivery status', 'ACTIVE'),
    ('integration.delivery.manage', 'Replay dead-letter deliveries', 'ACTIVE')
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    status = VALUES(status);

INSERT INTO tenant_role_permission (tenant_id, role_id, permission_key)
SELECT role.tenant_id, role.id, permission.permission_key
FROM tenant_role role
JOIN permission_definition permission
  ON permission.permission_key IN ('integration.delivery.read', 'integration.delivery.manage')
WHERE role.role_key = 'COMPANY_ADMIN'
  AND NOT EXISTS (
      SELECT 1
      FROM tenant_role_permission existing
      WHERE existing.tenant_id = role.tenant_id
        AND existing.role_id = role.id
        AND existing.permission_key = permission.permission_key
  );
