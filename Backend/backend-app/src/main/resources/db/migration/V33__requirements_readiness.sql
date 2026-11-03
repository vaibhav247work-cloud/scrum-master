INSERT INTO feature_definition (feature_key, display_name, status) VALUES
    ('REQUIREMENTS_READINESS', 'Requirements Readiness', 'ACTIVE')
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), status = VALUES(status);

-- MySQL requires the referenced tenant-scoped columns to be indexed in this
-- exact order for the composite foreign key below. This migration owns the
-- repair because V32 may already be applied in existing installations.
CREATE INDEX ix_demand_tenant_id ON demand_item (tenant_id, id);

CREATE TABLE requirement_item (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    demand_id BINARY(16) NULL,
    requirement_key VARCHAR(32) NOT NULL,
    title VARCHAR(240) NOT NULL,
    description TEXT NOT NULL,
    business_context TEXT NOT NULL,
    ux_context TEXT NOT NULL,
    technical_context TEXT NOT NULL,
    acceptance_criteria TEXT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    created_by BINARY(16) NOT NULL,
    reviewed_by BINARY(16) NULL,
    review_note VARCHAR(2000) NOT NULL DEFAULT '',
    reviewed_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_requirement_tenant_key (tenant_id, requirement_key),
    KEY ix_requirement_tenant_id (tenant_id, id),
    KEY ix_requirement_tenant_status_updated (tenant_id, status, updated_at, id),
    KEY ix_requirement_tenant_demand (tenant_id, demand_id, id),
    CONSTRAINT chk_requirement_status CHECK (status IN ('DRAFT', 'IN_REVIEW', 'CHANGES_REQUESTED', 'READY', 'REJECTED')),
    CONSTRAINT fk_requirement_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_requirement_demand_tenant FOREIGN KEY (tenant_id, demand_id) REFERENCES demand_item (tenant_id, id),
    CONSTRAINT fk_requirement_creator_tenant FOREIGN KEY (tenant_id, created_by) REFERENCES tenant_user (tenant_id, id),
    CONSTRAINT fk_requirement_reviewer_tenant FOREIGN KEY (tenant_id, reviewed_by) REFERENCES tenant_user (tenant_id, id)
) ENGINE=InnoDB;

CREATE TABLE requirement_readiness_check (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    requirement_id BINARY(16) NOT NULL,
    check_key VARCHAR(64) NOT NULL,
    label VARCHAR(240) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT TRUE,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    evidence VARCHAR(2000) NOT NULL DEFAULT '',
    updated_by BINARY(16) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_requirement_check (tenant_id, requirement_id, check_key),
    KEY ix_requirement_check_tenant_status (tenant_id, requirement_id, required, completed),
    CONSTRAINT fk_requirement_check_requirement_tenant FOREIGN KEY (tenant_id, requirement_id) REFERENCES requirement_item (tenant_id, id),
    CONSTRAINT fk_requirement_check_updater_tenant FOREIGN KEY (tenant_id, updated_by) REFERENCES tenant_user (tenant_id, id)
) ENGINE=InnoDB;

INSERT INTO permission_definition (permission_key, display_name, status) VALUES
    ('requirement.create', 'Create requirements', 'ACTIVE'),
    ('requirement.read', 'View requirements', 'ACTIVE'),
    ('requirement.manage', 'Manage readiness evidence', 'ACTIVE'),
    ('requirement.review', 'Review requirements for development readiness', 'ACTIVE')
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), status = VALUES(status);

INSERT INTO tenant_role_permission (tenant_id, role_id, permission_key)
SELECT role.tenant_id, role.id, permission.permission_key
FROM tenant_role role
JOIN permission_definition permission
  ON permission.permission_key IN ('requirement.create', 'requirement.read', 'requirement.manage', 'requirement.review')
WHERE role.role_key = 'COMPANY_ADMIN'
ON DUPLICATE KEY UPDATE permission_key = VALUES(permission_key);
