INSERT INTO permission_definition (permission_key, display_name, status) VALUES
    ('resource.allocation.read', 'View resource allocations', 'ACTIVE'),
    ('resource.allocation.manage', 'Manage resource allocations', 'ACTIVE')
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), status = VALUES(status);

INSERT INTO tenant_role_permission (tenant_id, role_id, permission_key)
SELECT role.tenant_id, role.id, permission.permission_key
FROM tenant_role role
JOIN permission_definition permission
  ON permission.permission_key IN ('resource.allocation.read', 'resource.allocation.manage')
WHERE role.role_key = 'COMPANY_ADMIN'
ON DUPLICATE KEY UPDATE permission_key = VALUES(permission_key);

CREATE TABLE resource_allocation (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    project_id BINARY(16) NOT NULL,
    sprint_id BINARY(16) NULL,
    team_id BINARY(16) NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    planned_minutes_per_week INT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    note VARCHAR(1000) NOT NULL DEFAULT '',
    created_by BINARY(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_resource_allocation_user_dates (tenant_id, user_id, status, start_date, end_date),
    KEY ix_resource_allocation_project_dates (tenant_id, project_id, status, start_date, end_date),
    KEY ix_resource_allocation_sprint (tenant_id, sprint_id, status, user_id),
    CONSTRAINT chk_resource_allocation_dates CHECK (end_date >= start_date),
    CONSTRAINT chk_resource_allocation_minutes CHECK (planned_minutes_per_week BETWEEN 1 AND 10080),
    CONSTRAINT chk_resource_allocation_status CHECK (status IN ('ACTIVE', 'RELEASED', 'CANCELLED')),
    CONSTRAINT fk_resource_allocation_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_resource_allocation_user FOREIGN KEY (tenant_id, user_id) REFERENCES tenant_user (tenant_id, id),
    CONSTRAINT fk_resource_allocation_project FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, id),
    CONSTRAINT fk_resource_allocation_sprint FOREIGN KEY (tenant_id, project_id, sprint_id) REFERENCES sprint (tenant_id, project_id, id),
    CONSTRAINT fk_resource_allocation_team FOREIGN KEY (tenant_id, team_id) REFERENCES team (tenant_id, id),
    CONSTRAINT fk_resource_allocation_creator FOREIGN KEY (tenant_id, created_by) REFERENCES tenant_user (tenant_id, id)
) ENGINE=InnoDB;
