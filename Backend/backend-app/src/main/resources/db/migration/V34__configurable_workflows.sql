INSERT INTO feature_definition (feature_key, display_name, status) VALUES
    ('PLANNING_WORKFLOWS', 'Configurable Planning Workflows', 'ACTIVE')
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), status = VALUES(status);

CREATE TABLE workflow_definition (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    workflow_key VARCHAR(32) NOT NULL,
    name VARCHAR(160) NOT NULL,
    methodology VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_by BINARY(16) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_workflow_tenant_key (tenant_id, workflow_key),
    KEY ix_workflow_tenant_id (tenant_id, id),
    CONSTRAINT chk_workflow_methodology CHECK (methodology IN ('SCRUM', 'KANBAN', 'HYBRID', 'CUSTOM')),
    CONSTRAINT chk_workflow_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT fk_workflow_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_workflow_created_by_tenant FOREIGN KEY (tenant_id, created_by) REFERENCES tenant_user (tenant_id, id)
) ENGINE=InnoDB;

CREATE TABLE workflow_status (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    workflow_id BINARY(16) NOT NULL,
    status_key VARCHAR(32) NOT NULL,
    label VARCHAR(120) NOT NULL,
    terminal BOOLEAN NOT NULL DEFAULT FALSE,
    position INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_workflow_status_key (tenant_id, workflow_id, status_key),
    UNIQUE KEY uq_workflow_status_id_scope (tenant_id, workflow_id, id),
    KEY ix_workflow_status_tenant_order (tenant_id, workflow_id, position, id),
    CONSTRAINT fk_workflow_status_workflow_tenant FOREIGN KEY (tenant_id, workflow_id) REFERENCES workflow_definition (tenant_id, id)
) ENGINE=InnoDB;

CREATE TABLE workflow_transition (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    workflow_id BINARY(16) NOT NULL,
    from_status_id BINARY(16) NOT NULL,
    to_status_id BINARY(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_workflow_transition (tenant_id, workflow_id, from_status_id, to_status_id),
    KEY ix_workflow_transition_target (tenant_id, workflow_id, to_status_id),
    CONSTRAINT fk_workflow_transition_workflow_tenant FOREIGN KEY (tenant_id, workflow_id) REFERENCES workflow_definition (tenant_id, id),
    CONSTRAINT fk_workflow_transition_from_scope FOREIGN KEY (tenant_id, workflow_id, from_status_id) REFERENCES workflow_status (tenant_id, workflow_id, id),
    CONSTRAINT fk_workflow_transition_to_scope FOREIGN KEY (tenant_id, workflow_id, to_status_id) REFERENCES workflow_status (tenant_id, workflow_id, id)
) ENGINE=InnoDB;

ALTER TABLE project
    ADD COLUMN workflow_id BINARY(16) NULL AFTER status,
    ADD KEY ix_project_tenant_workflow (tenant_id, workflow_id, id),
    ADD CONSTRAINT fk_project_workflow_tenant FOREIGN KEY (tenant_id, workflow_id)
        REFERENCES workflow_definition (tenant_id, id);

INSERT INTO permission_definition (permission_key, display_name, status) VALUES
    ('workflow.read', 'View planning workflows', 'ACTIVE'),
    ('workflow.manage', 'Manage planning workflows', 'ACTIVE')
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), status = VALUES(status);

INSERT INTO tenant_role_permission (tenant_id, role_id, permission_key)
SELECT role.tenant_id, role.id, permission.permission_key
FROM tenant_role role
JOIN permission_definition permission
  ON permission.permission_key IN ('workflow.read', 'workflow.manage')
WHERE role.role_key = 'COMPANY_ADMIN'
ON DUPLICATE KEY UPDATE permission_key = VALUES(permission_key);

INSERT INTO workflow_definition
    (id, tenant_id, workflow_key, name, methodology, status, created_by, created_at, updated_at)
SELECT UUID_TO_BIN(UUID()), tenant.id, 'DEFAULT', 'Default delivery workflow', 'HYBRID', 'ACTIVE', NULL,
       CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM tenant
WHERE NOT EXISTS (
    SELECT 1 FROM workflow_definition existing
    WHERE existing.tenant_id = tenant.id AND existing.workflow_key = 'DEFAULT'
);

INSERT INTO workflow_status (id, tenant_id, workflow_id, status_key, label, terminal, position, created_at)
SELECT UUID_TO_BIN(UUID()), workflow.tenant_id, workflow.id, 'TODO', 'To do', FALSE, 10, CURRENT_TIMESTAMP(6)
FROM workflow_definition workflow
WHERE workflow.workflow_key = 'DEFAULT'
  AND NOT EXISTS (SELECT 1 FROM workflow_status status WHERE status.tenant_id = workflow.tenant_id AND status.workflow_id = workflow.id AND status.status_key = 'TODO');
INSERT INTO workflow_status (id, tenant_id, workflow_id, status_key, label, terminal, position, created_at)
SELECT UUID_TO_BIN(UUID()), workflow.tenant_id, workflow.id, 'IN_PROGRESS', 'In progress', FALSE, 20, CURRENT_TIMESTAMP(6)
FROM workflow_definition workflow
WHERE workflow.workflow_key = 'DEFAULT'
  AND NOT EXISTS (SELECT 1 FROM workflow_status status WHERE status.tenant_id = workflow.tenant_id AND status.workflow_id = workflow.id AND status.status_key = 'IN_PROGRESS');
INSERT INTO workflow_status (id, tenant_id, workflow_id, status_key, label, terminal, position, created_at)
SELECT UUID_TO_BIN(UUID()), workflow.tenant_id, workflow.id, 'BLOCKED', 'Blocked', FALSE, 30, CURRENT_TIMESTAMP(6)
FROM workflow_definition workflow
WHERE workflow.workflow_key = 'DEFAULT'
  AND NOT EXISTS (SELECT 1 FROM workflow_status status WHERE status.tenant_id = workflow.tenant_id AND status.workflow_id = workflow.id AND status.status_key = 'BLOCKED');
INSERT INTO workflow_status (id, tenant_id, workflow_id, status_key, label, terminal, position, created_at)
SELECT UUID_TO_BIN(UUID()), workflow.tenant_id, workflow.id, 'DONE', 'Done', TRUE, 40, CURRENT_TIMESTAMP(6)
FROM workflow_definition workflow
WHERE workflow.workflow_key = 'DEFAULT'
  AND NOT EXISTS (SELECT 1 FROM workflow_status status WHERE status.tenant_id = workflow.tenant_id AND status.workflow_id = workflow.id AND status.status_key = 'DONE');
INSERT INTO workflow_status (id, tenant_id, workflow_id, status_key, label, terminal, position, created_at)
SELECT UUID_TO_BIN(UUID()), workflow.tenant_id, workflow.id, 'CANCELLED', 'Cancelled', TRUE, 50, CURRENT_TIMESTAMP(6)
FROM workflow_definition workflow
WHERE workflow.workflow_key = 'DEFAULT'
  AND NOT EXISTS (SELECT 1 FROM workflow_status status WHERE status.tenant_id = workflow.tenant_id AND status.workflow_id = workflow.id AND status.status_key = 'CANCELLED');

INSERT IGNORE INTO workflow_transition (id, tenant_id, workflow_id, from_status_id, to_status_id, created_at)
SELECT UUID_TO_BIN(UUID()), source.tenant_id, source.workflow_id, source.id, target.id, CURRENT_TIMESTAMP(6)
FROM workflow_status source
JOIN workflow_status target ON target.tenant_id = source.tenant_id AND target.workflow_id = source.workflow_id
WHERE source.status_key IN ('TODO', 'IN_PROGRESS', 'BLOCKED')
  AND target.status_key IN ('TODO', 'IN_PROGRESS', 'BLOCKED', 'DONE', 'CANCELLED')
  AND source.status_key <> target.status_key;

UPDATE project p
JOIN workflow_definition workflow ON workflow.tenant_id = p.tenant_id AND workflow.workflow_key = 'DEFAULT' AND workflow.status = 'ACTIVE'
SET p.workflow_id = workflow.id
WHERE p.workflow_id IS NULL;
