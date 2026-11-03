ALTER TABLE tenant_time_policy
    ADD COLUMN approval_enabled BOOLEAN NOT NULL DEFAULT FALSE AFTER allow_weekend,
    ADD COLUMN approval_cadence VARCHAR(16) NOT NULL DEFAULT 'WEEKLY' AFTER approval_enabled,
    ADD COLUMN auto_approve BOOLEAN NOT NULL DEFAULT FALSE AFTER approval_cadence,
    ADD CONSTRAINT chk_time_policy_approval_cadence CHECK (approval_cadence IN ('DAILY', 'WEEKLY', 'MONTHLY'));

ALTER TABLE time_entry
    ADD COLUMN approval_status VARCHAR(16) NOT NULL DEFAULT 'APPROVED' AFTER source,
    ADD COLUMN approved_by BINARY(16) NULL AFTER approval_status,
    ADD COLUMN approved_at TIMESTAMP(6) NULL AFTER approved_by,
    ADD COLUMN approval_note VARCHAR(1000) NOT NULL DEFAULT '' AFTER approved_at,
    ADD KEY ix_time_entry_tenant_approval_date (tenant_id, approval_status, entry_date, id),
    ADD CONSTRAINT chk_time_entry_approval_status CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    ADD CONSTRAINT fk_time_entry_approved_by_tenant FOREIGN KEY (tenant_id, approved_by)
        REFERENCES tenant_user (tenant_id, id);

INSERT INTO permission_definition (permission_key, display_name, status) VALUES
    ('time.entry.approve', 'Approve or reject time entries', 'ACTIVE')
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), status = VALUES(status);

INSERT INTO tenant_role_permission (tenant_id, role_id, permission_key)
SELECT role.tenant_id, role.id, 'time.entry.approve'
FROM tenant_role role
WHERE role.role_key = 'COMPANY_ADMIN'
ON DUPLICATE KEY UPDATE permission_key = VALUES(permission_key);
