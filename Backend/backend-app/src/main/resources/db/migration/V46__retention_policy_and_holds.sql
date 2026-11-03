INSERT INTO feature_definition (feature_key, display_name, status) VALUES
    ('RETENTION_MANAGEMENT', 'Retention and Data Lifecycle Management', 'ACTIVE')
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), status = VALUES(status);

INSERT INTO permission_definition (permission_key, display_name, status) VALUES
    ('retention.read', 'View retention policy and previews', 'ACTIVE'),
    ('retention.manage', 'Manage retention policy and legal holds', 'ACTIVE'),
    ('retention.execute', 'Execute retention cleanup', 'ACTIVE')
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), status = VALUES(status);

CREATE TABLE tenant_retention_policy (
    tenant_id BINARY(16) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    operation_mode VARCHAR(32) NOT NULL DEFAULT 'PREVIEW_ONLY',
    activity_retention_days INT NOT NULL DEFAULT 120,
    notification_retention_days INT NOT NULL DEFAULT 90,
    delivery_retention_days INT NOT NULL DEFAULT 90,
    audit_retention_days INT NOT NULL DEFAULT 365,
    policy_version INT NOT NULL DEFAULT 1,
    updated_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (tenant_id),
    CONSTRAINT chk_retention_mode CHECK (operation_mode IN ('PURGE', 'PREVIEW_ONLY')),
    CONSTRAINT chk_retention_activity_days CHECK (activity_retention_days BETWEEN 120 AND 3650),
    CONSTRAINT chk_retention_notification_days CHECK (notification_retention_days BETWEEN 30 AND 3650),
    CONSTRAINT chk_retention_delivery_days CHECK (delivery_retention_days BETWEEN 30 AND 3650),
    CONSTRAINT chk_retention_audit_days CHECK (audit_retention_days BETWEEN 365 AND 3650),
    CONSTRAINT fk_retention_policy_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_retention_policy_updated_by FOREIGN KEY (tenant_id, updated_by) REFERENCES tenant_user (tenant_id, id)
) ENGINE=InnoDB;

CREATE TABLE tenant_retention_hold (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    hold_key VARCHAR(120) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BINARY(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    released_by BINARY(16) NULL,
    released_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_retention_hold_key (tenant_id, hold_key),
    KEY ix_retention_hold_active (tenant_id, active, created_at, id),
    CONSTRAINT fk_retention_hold_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_retention_hold_created_by FOREIGN KEY (tenant_id, created_by) REFERENCES tenant_user (tenant_id, id),
    CONSTRAINT fk_retention_hold_released_by FOREIGN KEY (tenant_id, released_by) REFERENCES tenant_user (tenant_id, id)
) ENGINE=InnoDB;

CREATE TABLE retention_cleanup_run (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    actor_id BINARY(16) NULL,
    status VARCHAR(32) NOT NULL,
    legal_hold_active BOOLEAN NOT NULL,
    notifications_removed INT NOT NULL DEFAULT 0,
    deliveries_removed INT NOT NULL DEFAULT 0,
    outbox_events_removed INT NOT NULL DEFAULT 0,
    audit_events_removed INT NOT NULL DEFAULT 0,
    started_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    KEY ix_retention_run_tenant_time (tenant_id, started_at, id),
    CONSTRAINT fk_retention_run_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_retention_run_actor FOREIGN KEY (tenant_id, actor_id) REFERENCES tenant_user (tenant_id, id)
) ENGINE=InnoDB;

INSERT INTO tenant_role_permission (tenant_id, role_id, permission_key)
SELECT r.tenant_id, r.id, p.permission_key
FROM tenant_role r CROSS JOIN permission_definition p
WHERE r.role_key = 'COMPANY_ADMIN' AND r.status = 'ACTIVE'
  AND p.permission_key IN ('retention.read', 'retention.manage', 'retention.execute')
ON DUPLICATE KEY UPDATE permission_key = VALUES(permission_key);
