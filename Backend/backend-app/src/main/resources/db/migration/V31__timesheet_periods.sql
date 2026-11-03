CREATE TABLE timesheet_period (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    cadence VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    submitted_at TIMESTAMP(6) NULL,
    submitted_by BINARY(16) NULL,
    decided_at TIMESTAMP(6) NULL,
    decided_by BINARY(16) NULL,
    decision_note VARCHAR(1000) NOT NULL DEFAULT '',
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_timesheet_period (tenant_id, user_id, period_start, period_end, cadence),
    KEY ix_timesheet_tenant_status (tenant_id, status, period_end, id),
    KEY ix_timesheet_user_period (tenant_id, user_id, period_start, period_end, id),
    CONSTRAINT chk_timesheet_period_dates CHECK (period_start <= period_end),
    CONSTRAINT chk_timesheet_cadence CHECK (cadence IN ('DAILY', 'WEEKLY', 'MONTHLY')),
    CONSTRAINT chk_timesheet_status CHECK (status IN ('OPEN', 'SUBMITTED', 'APPROVED', 'REJECTED', 'REOPENED')),
    CONSTRAINT fk_timesheet_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_timesheet_user_tenant FOREIGN KEY (tenant_id, user_id) REFERENCES tenant_user (tenant_id, id),
    CONSTRAINT fk_timesheet_submitted_by_tenant FOREIGN KEY (tenant_id, submitted_by) REFERENCES tenant_user (tenant_id, id),
    CONSTRAINT fk_timesheet_decided_by_tenant FOREIGN KEY (tenant_id, decided_by) REFERENCES tenant_user (tenant_id, id)
) ENGINE=InnoDB;

INSERT INTO permission_definition (permission_key, display_name, status) VALUES
    ('time.timesheet.read', 'View own timesheets', 'ACTIVE'),
    ('time.timesheet.submit', 'Submit own timesheets', 'ACTIVE'),
    ('time.timesheet.approve', 'Approve or reject timesheets', 'ACTIVE')
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    status = VALUES(status);

INSERT INTO tenant_role_permission (tenant_id, role_id, permission_key)
SELECT role.tenant_id, role.id, permission.permission_key
FROM tenant_role role
JOIN permission_definition permission
  ON permission.permission_key IN ('time.timesheet.read', 'time.timesheet.submit', 'time.timesheet.approve')
WHERE role.role_key = 'COMPANY_ADMIN'
ON DUPLICATE KEY UPDATE permission_key = VALUES(permission_key);

-- Preserve the existing time-entry role model for tenants already onboarded.
-- Timesheets are the period view over the same central time system, so anyone
-- who could create/read entries receives the corresponding self-service access.
INSERT INTO tenant_role_permission (tenant_id, role_id, permission_key)
SELECT role.tenant_id, role.id, 'time.timesheet.read'
FROM tenant_role role
JOIN tenant_role_permission existing
  ON existing.tenant_id = role.tenant_id AND existing.role_id = role.id
WHERE existing.permission_key = 'time.entry.read'
ON DUPLICATE KEY UPDATE permission_key = VALUES(permission_key);

INSERT INTO tenant_role_permission (tenant_id, role_id, permission_key)
SELECT role.tenant_id, role.id, 'time.timesheet.submit'
FROM tenant_role role
JOIN tenant_role_permission existing
  ON existing.tenant_id = role.tenant_id AND existing.role_id = role.id
WHERE existing.permission_key = 'time.entry.create'
ON DUPLICATE KEY UPDATE permission_key = VALUES(permission_key);

INSERT INTO tenant_role_permission (tenant_id, role_id, permission_key)
SELECT role.tenant_id, role.id, 'time.timesheet.approve'
FROM tenant_role role
JOIN tenant_role_permission existing
  ON existing.tenant_id = role.tenant_id AND existing.role_id = role.id
WHERE existing.permission_key = 'time.entry.approve'
ON DUPLICATE KEY UPDATE permission_key = VALUES(permission_key);
