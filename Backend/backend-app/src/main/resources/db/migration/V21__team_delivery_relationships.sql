INSERT INTO permission_definition (permission_key, display_name, status) VALUES
    ('project.team.read', 'View project teams', 'ACTIVE'),
    ('project.team.manage', 'Manage project teams', 'ACTIVE'),
    ('sprint.team.read', 'View sprint teams', 'ACTIVE'),
    ('sprint.team.manage', 'Manage sprint teams', 'ACTIVE')
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), status = VALUES(status);

INSERT INTO tenant_role_permission (tenant_id, role_id, permission_key)
SELECT role.tenant_id, role.id, permissions.permission_key
FROM tenant_role role
JOIN (
    SELECT 'project.team.read' AS permission_key
    UNION ALL SELECT 'project.team.manage'
    UNION ALL SELECT 'sprint.team.read'
    UNION ALL SELECT 'sprint.team.manage'
) permissions ON 1 = 1
WHERE role.role_key = 'COMPANY_ADMIN'
ON DUPLICATE KEY UPDATE permission_key = VALUES(permission_key);

CREATE TABLE project_team (
    tenant_id BINARY(16) NOT NULL,
    project_id BINARY(16) NOT NULL,
    team_id BINARY(16) NOT NULL,
    relationship_role VARCHAR(32) NOT NULL DEFAULT 'DELIVERY',
    created_by BINARY(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (tenant_id, project_id, team_id),
    KEY ix_project_team_team (tenant_id, team_id, project_id),
    CONSTRAINT chk_project_team_role CHECK (relationship_role IN ('OWNER', 'DELIVERY', 'CONTRIBUTOR')),
    CONSTRAINT fk_project_team_project FOREIGN KEY (tenant_id, project_id)
        REFERENCES project (tenant_id, id),
    CONSTRAINT fk_project_team_team FOREIGN KEY (tenant_id, team_id)
        REFERENCES team (tenant_id, id),
    CONSTRAINT fk_project_team_creator FOREIGN KEY (tenant_id, created_by)
        REFERENCES tenant_user (tenant_id, id)
) ENGINE=InnoDB;

CREATE TABLE sprint_team (
    tenant_id BINARY(16) NOT NULL,
    project_id BINARY(16) NOT NULL,
    sprint_id BINARY(16) NOT NULL,
    team_id BINARY(16) NOT NULL,
    relationship_role VARCHAR(32) NOT NULL DEFAULT 'DELIVERY',
    created_by BINARY(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (tenant_id, sprint_id, team_id),
    KEY ix_sprint_team_team (tenant_id, team_id, sprint_id),
    CONSTRAINT chk_sprint_team_role CHECK (relationship_role IN ('OWNER', 'DELIVERY', 'CONTRIBUTOR')),
    CONSTRAINT fk_sprint_team_sprint FOREIGN KEY (tenant_id, project_id, sprint_id)
        REFERENCES sprint (tenant_id, project_id, id),
    CONSTRAINT fk_sprint_team_team FOREIGN KEY (tenant_id, team_id)
        REFERENCES team (tenant_id, id),
    CONSTRAINT fk_sprint_team_creator FOREIGN KEY (tenant_id, created_by)
        REFERENCES tenant_user (tenant_id, id)
) ENGINE=InnoDB;

ALTER TABLE work_item
    ADD COLUMN team_id BINARY(16) NULL AFTER assignee_user_id,
    ADD KEY ix_work_item_tenant_team (tenant_id, team_id, status, id),
    ADD CONSTRAINT fk_work_item_team_tenant FOREIGN KEY (tenant_id, team_id)
        REFERENCES team (tenant_id, id);
