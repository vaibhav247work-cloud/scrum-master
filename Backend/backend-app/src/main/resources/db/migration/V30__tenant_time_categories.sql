CREATE TABLE tenant_time_category (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    category_key VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_time_category_tenant_key (tenant_id, category_key),
    KEY ix_time_category_tenant_enabled (tenant_id, enabled, category_key),
    CONSTRAINT fk_time_category_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id)
) ENGINE=InnoDB;

ALTER TABLE time_entry
    ADD COLUMN category_key VARCHAR(64) NOT NULL DEFAULT 'GENERAL' AFTER source,
    ADD KEY ix_time_entry_tenant_category_date (tenant_id, category_key, entry_date, id);

INSERT INTO permission_definition (permission_key, display_name, status) VALUES
    ('time.category.read', 'View time categories', 'ACTIVE'),
    ('time.category.manage', 'Manage time categories', 'ACTIVE')
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), status = VALUES(status);

INSERT INTO tenant_role_permission (tenant_id, role_id, permission_key)
SELECT role.tenant_id, role.id, permission.permission_key
FROM tenant_role role
JOIN permission_definition permission
  ON permission.permission_key IN ('time.category.read', 'time.category.manage')
WHERE role.role_key = 'COMPANY_ADMIN'
ON DUPLICATE KEY UPDATE permission_key = VALUES(permission_key);
