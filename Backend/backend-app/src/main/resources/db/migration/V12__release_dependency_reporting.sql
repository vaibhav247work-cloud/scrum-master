INSERT INTO feature_definition (feature_key, display_name, status) VALUES
    ('DEPENDENCY_RISK', 'Dependency and Risk Management', 'ACTIVE')
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), status = VALUES(status);

INSERT INTO permission_definition (permission_key, display_name, status) VALUES
    ('release.read', 'View releases', 'ACTIVE'),
    ('release.manage', 'Manage releases', 'ACTIVE'),
    ('dependency.read', 'View work dependencies', 'ACTIVE'),
    ('dependency.manage', 'Manage work dependencies', 'ACTIVE'),
    ('control.tower.read', 'View company control tower', 'ACTIVE')
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), status = VALUES(status);

INSERT INTO tenant_role_permission (tenant_id, role_id, permission_key)
SELECT role.tenant_id, role.id, permission.permission_key
FROM tenant_role role
JOIN permission_definition permission
  ON permission.permission_key IN ('release.read', 'release.manage', 'dependency.read',
                                   'dependency.manage', 'control.tower.read')
WHERE role.role_key = 'COMPANY_ADMIN'
ON DUPLICATE KEY UPDATE permission_key = VALUES(permission_key);

CREATE TABLE release_item (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    project_id BINARY(16) NOT NULL,
    release_key VARCHAR(40) NOT NULL,
    name VARCHAR(240) NOT NULL,
    description VARCHAR(4000) NOT NULL,
    target_date DATE NULL,
    status VARCHAR(32) NOT NULL,
    created_by BINARY(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_release_tenant_project_key (tenant_id, project_id, release_key),
    UNIQUE KEY uq_release_tenant_id (tenant_id, id),
    KEY ix_release_tenant_project_status_date (tenant_id, project_id, status, target_date, id),
    CONSTRAINT fk_release_project_tenant FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, id),
    CONSTRAINT fk_release_created_by_tenant FOREIGN KEY (tenant_id, created_by) REFERENCES tenant_user (tenant_id, id),
    CONSTRAINT fk_release_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id)
) ENGINE=InnoDB;

CREATE TABLE release_work_item (
    tenant_id BINARY(16) NOT NULL,
    release_id BINARY(16) NOT NULL,
    work_item_id BINARY(16) NOT NULL,
    added_by BINARY(16) NOT NULL,
    added_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (tenant_id, release_id, work_item_id),
    KEY ix_release_work_item_work (tenant_id, work_item_id),
    CONSTRAINT fk_release_work_release FOREIGN KEY (tenant_id, release_id) REFERENCES release_item (tenant_id, id),
    CONSTRAINT fk_release_work_item FOREIGN KEY (tenant_id, work_item_id) REFERENCES work_item (tenant_id, id),
    CONSTRAINT fk_release_work_added_by FOREIGN KEY (tenant_id, added_by) REFERENCES tenant_user (tenant_id, id)
) ENGINE=InnoDB;

CREATE TABLE work_item_dependency (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    blocked_work_item_id BINARY(16) NOT NULL,
    depends_on_work_item_id BINARY(16) NOT NULL,
    dependency_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_by BINARY(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_dependency_edge (tenant_id, blocked_work_item_id, depends_on_work_item_id, dependency_type),
    KEY ix_dependency_tenant_status (tenant_id, status, created_at, id),
    KEY ix_dependency_tenant_depends_on (tenant_id, depends_on_work_item_id, status, id),
    CONSTRAINT chk_dependency_not_self CHECK (blocked_work_item_id <> depends_on_work_item_id),
    CONSTRAINT fk_dependency_blocked_item FOREIGN KEY (tenant_id, blocked_work_item_id) REFERENCES work_item (tenant_id, id),
    CONSTRAINT fk_dependency_depends_item FOREIGN KEY (tenant_id, depends_on_work_item_id) REFERENCES work_item (tenant_id, id),
    CONSTRAINT fk_dependency_created_by FOREIGN KEY (tenant_id, created_by) REFERENCES tenant_user (tenant_id, id)
) ENGINE=InnoDB;
