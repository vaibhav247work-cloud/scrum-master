ALTER TABLE work_item
    ADD UNIQUE KEY uq_work_item_tenant_id (tenant_id, id);

INSERT INTO permission_definition (permission_key, display_name, status) VALUES
    ('time.entry.create', 'Create own time entries', 'ACTIVE'),
    ('time.entry.read', 'View own time entries', 'ACTIVE')
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    status = VALUES(status);

INSERT INTO tenant_role_permission (tenant_id, role_id, permission_key)
SELECT role.tenant_id, role.id, permission.permission_key
FROM tenant_role role
JOIN permission_definition permission
  ON permission.permission_key IN ('time.entry.create', 'time.entry.read')
WHERE role.role_key = 'COMPANY_ADMIN'
ON DUPLICATE KEY UPDATE permission_key = VALUES(permission_key);

CREATE TABLE time_entry (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    work_item_id BINARY(16) NULL,
    project_id BINARY(16) NULL,
    sprint_id BINARY(16) NULL,
    entry_date DATE NOT NULL,
    minutes INT NOT NULL,
    description VARCHAR(4000) NOT NULL,
    source VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_time_entry_tenant_user_date (tenant_id, user_id, entry_date, id),
    KEY ix_time_entry_tenant_work_date (tenant_id, work_item_id, entry_date, id),
    KEY ix_time_entry_tenant_project_date (tenant_id, project_id, entry_date, id),
    CONSTRAINT chk_time_entry_minutes CHECK (minutes BETWEEN 1 AND 1440),
    CONSTRAINT chk_time_entry_sprint_requires_project CHECK (sprint_id IS NULL OR project_id IS NOT NULL),
    CONSTRAINT chk_time_entry_work_context_exclusive CHECK (
        work_item_id IS NULL OR (project_id IS NULL AND sprint_id IS NULL)
    ),
    CONSTRAINT fk_time_entry_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_time_entry_user_tenant FOREIGN KEY (tenant_id, user_id)
        REFERENCES tenant_user (tenant_id, id),
    CONSTRAINT fk_time_entry_work_tenant FOREIGN KEY (tenant_id, work_item_id)
        REFERENCES work_item (tenant_id, id),
    CONSTRAINT fk_time_entry_project_tenant FOREIGN KEY (tenant_id, project_id)
        REFERENCES project (tenant_id, id),
    CONSTRAINT fk_time_entry_sprint_project_tenant FOREIGN KEY (tenant_id, project_id, sprint_id)
        REFERENCES sprint (tenant_id, project_id, id)
) ENGINE=InnoDB;
