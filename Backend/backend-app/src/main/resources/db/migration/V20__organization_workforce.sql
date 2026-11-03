INSERT INTO feature_definition (feature_key, display_name, status) VALUES
    ('ORGANIZATION_WORKFORCE', 'Organization and Workforce', 'ACTIVE')
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), status = VALUES(status);

INSERT INTO permission_definition (permission_key, display_name, status) VALUES
    ('organization.read', 'View organization structure', 'ACTIVE'),
    ('organization.manage', 'Manage organization structure', 'ACTIVE'),
    ('team.read', 'View teams and memberships', 'ACTIVE'),
    ('team.manage', 'Manage teams and memberships', 'ACTIVE')
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), status = VALUES(status);

CREATE TABLE organization_unit (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    parent_unit_id BINARY(16) NULL,
    unit_key VARCHAR(80) NOT NULL,
    name VARCHAR(200) NOT NULL,
    unit_type VARCHAR(32) NOT NULL,
    manager_user_id BINARY(16) NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_org_unit_tenant_key (tenant_id, unit_key),
    UNIQUE KEY uq_org_unit_tenant_id (tenant_id, id),
    KEY ix_org_unit_parent (tenant_id, parent_unit_id, status),
    KEY ix_org_unit_manager (tenant_id, manager_user_id, status),
    CONSTRAINT chk_org_unit_type CHECK (unit_type IN ('BUSINESS_UNIT', 'DEPARTMENT')),
    CONSTRAINT fk_org_unit_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_org_unit_parent FOREIGN KEY (tenant_id, parent_unit_id)
        REFERENCES organization_unit (tenant_id, id),
    CONSTRAINT fk_org_unit_manager FOREIGN KEY (tenant_id, manager_user_id)
        REFERENCES tenant_user (tenant_id, id)
) ENGINE=InnoDB;

CREATE TABLE team (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    parent_unit_id BINARY(16) NULL,
    team_key VARCHAR(80) NOT NULL,
    name VARCHAR(200) NOT NULL,
    manager_user_id BINARY(16) NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_team_tenant_key (tenant_id, team_key),
    UNIQUE KEY uq_team_tenant_id (tenant_id, id),
    KEY ix_team_parent (tenant_id, parent_unit_id, status),
    KEY ix_team_manager (tenant_id, manager_user_id, status),
    CONSTRAINT fk_team_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_team_parent_unit FOREIGN KEY (tenant_id, parent_unit_id)
        REFERENCES organization_unit (tenant_id, id),
    CONSTRAINT fk_team_manager FOREIGN KEY (tenant_id, manager_user_id)
        REFERENCES tenant_user (tenant_id, id)
) ENGINE=InnoDB;

CREATE TABLE team_membership (
    tenant_id BINARY(16) NOT NULL,
    team_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    membership_role VARCHAR(32) NOT NULL DEFAULT 'MEMBER',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    joined_at TIMESTAMP(6) NOT NULL,
    left_at TIMESTAMP(6) NULL,
    created_by BINARY(16) NOT NULL,
    PRIMARY KEY (tenant_id, team_id, user_id),
    KEY ix_team_membership_user (tenant_id, user_id, status, team_id),
    KEY ix_team_membership_team (tenant_id, team_id, status, user_id),
    CONSTRAINT chk_team_membership_role CHECK (membership_role IN ('MEMBER', 'LEAD', 'BACKUP_LEAD')),
    CONSTRAINT chk_team_membership_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT fk_team_membership_team FOREIGN KEY (tenant_id, team_id)
        REFERENCES team (tenant_id, id),
    CONSTRAINT fk_team_membership_user FOREIGN KEY (tenant_id, user_id)
        REFERENCES tenant_user (tenant_id, id),
    CONSTRAINT fk_team_membership_creator FOREIGN KEY (tenant_id, created_by)
        REFERENCES tenant_user (tenant_id, id)
) ENGINE=InnoDB;

INSERT INTO tenant_role_permission (tenant_id, role_id, permission_key)
SELECT role.tenant_id, role.id, permissions.permission_key
FROM tenant_role role
JOIN (
    SELECT 'organization.read' AS permission_key
    UNION ALL SELECT 'organization.manage'
    UNION ALL SELECT 'team.read'
    UNION ALL SELECT 'team.manage'
) permissions ON 1 = 1
WHERE role.role_key = 'COMPANY_ADMIN'
ON DUPLICATE KEY UPDATE permission_key = VALUES(permission_key);
