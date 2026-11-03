CREATE TABLE resource_user_capacity (
    tenant_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    weekly_capacity_minutes INT NOT NULL DEFAULT 2400,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (tenant_id, user_id),
    CONSTRAINT chk_resource_capacity_minutes CHECK (weekly_capacity_minutes BETWEEN 60 AND 10080),
    CONSTRAINT fk_resource_capacity_user_tenant FOREIGN KEY (tenant_id, user_id)
        REFERENCES tenant_user (tenant_id, id)
) ENGINE=InnoDB;

INSERT INTO permission_definition (permission_key, display_name, status) VALUES
    ('resource.workload.read', 'View manager workload', 'ACTIVE'),
    ('resource.capacity.manage', 'Manage resource capacity', 'ACTIVE')
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    status = VALUES(status);

INSERT INTO tenant_role_permission (tenant_id, role_id, permission_key)
SELECT role.tenant_id, role.id, permission.permission_key
FROM tenant_role role
JOIN permission_definition permission
  ON permission.permission_key IN ('resource.workload.read', 'resource.capacity.manage')
WHERE role.role_key = 'COMPANY_ADMIN'
ON DUPLICATE KEY UPDATE permission_key = VALUES(permission_key);
