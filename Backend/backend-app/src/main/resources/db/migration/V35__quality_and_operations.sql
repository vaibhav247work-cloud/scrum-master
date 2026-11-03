INSERT INTO feature_definition (feature_key, display_name, status) VALUES
    ('QUALITY_GATES', 'Quality and Release Gates', 'ACTIVE'),
    ('OPERATIONS_INCIDENTS', 'Operations Incident Management', 'ACTIVE')
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), status = VALUES(status);

INSERT INTO permission_definition (permission_key, display_name, status) VALUES
    ('quality.read', 'View quality gates', 'ACTIVE'),
    ('quality.manage', 'Manage quality gates', 'ACTIVE'),
    ('quality.waive', 'Waive quality gates', 'ACTIVE'),
    ('incident.read', 'View incidents', 'ACTIVE'),
    ('incident.manage', 'Manage incidents', 'ACTIVE')
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), status = VALUES(status);

INSERT INTO tenant_role_permission (tenant_id, role_id, permission_key)
SELECT role.tenant_id, role.id, permission.permission_key
FROM tenant_role role
JOIN permission_definition permission
  ON permission.permission_key IN ('quality.read', 'quality.manage', 'quality.waive', 'incident.read', 'incident.manage')
WHERE role.role_key = 'COMPANY_ADMIN'
ON DUPLICATE KEY UPDATE permission_key = VALUES(permission_key);

CREATE TABLE quality_gate (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    project_id BINARY(16) NULL,
    release_id BINARY(16) NULL,
    gate_key VARCHAR(32) NOT NULL,
    gate_type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    required BOOLEAN NOT NULL DEFAULT TRUE,
    evidence TEXT NOT NULL,
    decided_by BINARY(16) NULL,
    decided_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_quality_gate_tenant_scope_key (tenant_id, project_id, release_id, gate_key),
    KEY ix_quality_gate_tenant_status (tenant_id, status, updated_at, id),
    KEY ix_quality_gate_tenant_release (tenant_id, release_id, status, id),
    CONSTRAINT chk_quality_gate_type CHECK (gate_type IN ('QA', 'SECURITY', 'COMPLIANCE', 'APPROVAL')),
    CONSTRAINT chk_quality_gate_status CHECK (status IN ('OPEN', 'PASSED', 'FAILED', 'WAIVED')),
    CONSTRAINT fk_quality_gate_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_quality_gate_project FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, id),
    CONSTRAINT fk_quality_gate_release FOREIGN KEY (tenant_id, release_id) REFERENCES release_item (tenant_id, id),
    CONSTRAINT fk_quality_gate_decider FOREIGN KEY (tenant_id, decided_by) REFERENCES tenant_user (tenant_id, id)
) ENGINE=InnoDB;

CREATE TABLE operations_incident (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    project_id BINARY(16) NULL,
    release_id BINARY(16) NULL,
    incident_key VARCHAR(32) NOT NULL,
    title VARCHAR(240) NOT NULL,
    description TEXT NOT NULL,
    severity VARCHAR(2) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    owner_user_id BINARY(16) NULL,
    mitigation TEXT NOT NULL,
    root_cause TEXT NOT NULL,
    opened_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_incident_tenant_key (tenant_id, incident_key),
    KEY ix_incident_tenant_status_severity (tenant_id, status, severity, updated_at, id),
    KEY ix_incident_tenant_project (tenant_id, project_id, status, id),
    CONSTRAINT chk_incident_severity CHECK (severity IN ('S1', 'S2', 'S3', 'S4')),
    CONSTRAINT chk_incident_status CHECK (status IN ('OPEN', 'INVESTIGATING', 'MITIGATED', 'RESOLVED', 'CLOSED')),
    CONSTRAINT fk_incident_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_incident_project FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, id),
    CONSTRAINT fk_incident_release FOREIGN KEY (tenant_id, release_id) REFERENCES release_item (tenant_id, id),
    CONSTRAINT fk_incident_owner FOREIGN KEY (tenant_id, owner_user_id) REFERENCES tenant_user (tenant_id, id)
) ENGINE=InnoDB;
