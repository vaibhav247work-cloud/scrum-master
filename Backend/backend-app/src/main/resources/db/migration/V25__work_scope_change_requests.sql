INSERT INTO permission_definition (permission_key, display_name, status) VALUES
    ('work.change.request', 'Request work priority or scope changes', 'ACTIVE'),
    ('work.change.approve', 'Approve work priority or scope changes', 'ACTIVE')
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), status = VALUES(status);

INSERT INTO tenant_role_permission (tenant_id, role_id, permission_key)
SELECT role.tenant_id, role.id, permission.permission_key
FROM tenant_role role
JOIN permission_definition permission
  ON permission.permission_key IN ('work.change.request', 'work.change.approve')
WHERE role.role_key = 'COMPANY_ADMIN'
ON DUPLICATE KEY UPDATE permission_key = VALUES(permission_key);

CREATE TABLE work_scope_change_request (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    work_item_id BINARY(16) NOT NULL,
    project_id BINARY(16) NULL,
    requested_priority VARCHAR(32) NULL,
    target_sprint_id BINARY(16) NULL,
    requested_by BINARY(16) NOT NULL,
    emergency BOOLEAN NOT NULL DEFAULT FALSE,
    reason VARCHAR(4000) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'REQUESTED',
    decided_by BINARY(16) NULL,
    decision_note VARCHAR(1000) NOT NULL DEFAULT '',
    created_at TIMESTAMP(6) NOT NULL,
    decided_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    KEY ix_scope_change_work_status (tenant_id, work_item_id, status, created_at, id),
    KEY ix_scope_change_pending (tenant_id, status, emergency, created_at, id),
    CONSTRAINT chk_scope_change_status CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_scope_change_reason CHECK (CHAR_LENGTH(reason) > 0),
    CONSTRAINT chk_scope_change_target_project CHECK (target_sprint_id IS NULL OR project_id IS NOT NULL),
    CONSTRAINT fk_scope_change_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_scope_change_work FOREIGN KEY (tenant_id, work_item_id) REFERENCES work_item (tenant_id, id),
    CONSTRAINT fk_scope_change_project FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, id),
    CONSTRAINT fk_scope_change_sprint FOREIGN KEY (tenant_id, project_id, target_sprint_id)
        REFERENCES sprint (tenant_id, project_id, id),
    CONSTRAINT fk_scope_change_requester FOREIGN KEY (tenant_id, requested_by) REFERENCES tenant_user (tenant_id, id),
    CONSTRAINT fk_scope_change_decider FOREIGN KEY (tenant_id, decided_by) REFERENCES tenant_user (tenant_id, id)
) ENGINE=InnoDB;
