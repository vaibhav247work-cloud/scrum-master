ALTER TABLE tenant
    ADD COLUMN suspended_at TIMESTAMP(6) NULL AFTER status,
    ADD COLUMN deletion_scheduled_at TIMESTAMP(6) NULL AFTER suspended_at,
    ADD COLUMN lifecycle_reason VARCHAR(500) NULL AFTER deletion_scheduled_at;

CREATE TABLE tenant_lifecycle_event (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    actor_id BINARY(16) NULL,
    from_status VARCHAR(32) NULL,
    to_status VARCHAR(32) NOT NULL,
    reason VARCHAR(500) NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_tenant_lifecycle_tenant_time (tenant_id, occurred_at, id),
    CONSTRAINT fk_tenant_lifecycle_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id)
) ENGINE=InnoDB;

INSERT IGNORE INTO permission_definition (permission_key, display_name, status)
VALUES
    ('tenant.lifecycle.read', 'View tenant lifecycle', 'ACTIVE'),
    ('tenant.lifecycle.manage', 'Manage tenant lifecycle', 'ACTIVE'),
    ('tenant.data.export', 'Export tenant data', 'ACTIVE');

INSERT IGNORE INTO tenant_role_permission (tenant_id, role_id, permission_key)
SELECT r.tenant_id, r.id, p.permission_key
FROM tenant_role r
JOIN permission_definition p ON p.permission_key IN ('tenant.lifecycle.read', 'tenant.lifecycle.manage', 'tenant.data.export')
WHERE r.role_key = 'COMPANY_ADMIN' AND r.status = 'ACTIVE';
