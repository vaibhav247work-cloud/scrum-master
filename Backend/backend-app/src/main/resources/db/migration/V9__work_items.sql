ALTER TABLE sprint
    ADD UNIQUE KEY uq_sprint_tenant_project_id (tenant_id, project_id, id);

CREATE TABLE work_item (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    work_key VARCHAR(40) NOT NULL,
    project_id BINARY(16) NULL,
    sprint_id BINARY(16) NULL,
    assignee_user_id BINARY(16) NULL,
    title VARCHAR(240) NOT NULL,
    description VARCHAR(4000) NOT NULL,
    work_type VARCHAR(32) NOT NULL,
    priority VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_by BINARY(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_work_item_tenant_key (tenant_id, work_key),
    KEY ix_work_item_tenant_status_created (tenant_id, status, created_at, id),
    KEY ix_work_item_tenant_project (tenant_id, project_id, status, id),
    KEY ix_work_item_tenant_sprint (tenant_id, sprint_id, status, id),
    KEY ix_work_item_tenant_assignee (tenant_id, assignee_user_id, status, id),
    CONSTRAINT chk_work_item_sprint_requires_project CHECK (sprint_id IS NULL OR project_id IS NOT NULL),
    CONSTRAINT fk_work_item_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_work_item_project_tenant FOREIGN KEY (tenant_id, project_id)
        REFERENCES project (tenant_id, id),
    CONSTRAINT fk_work_item_sprint_project_tenant FOREIGN KEY (tenant_id, project_id, sprint_id)
        REFERENCES sprint (tenant_id, project_id, id),
    CONSTRAINT fk_work_item_assignee_tenant FOREIGN KEY (tenant_id, assignee_user_id)
        REFERENCES tenant_user (tenant_id, id),
    CONSTRAINT fk_work_item_created_by_tenant FOREIGN KEY (tenant_id, created_by)
        REFERENCES tenant_user (tenant_id, id)
) ENGINE=InnoDB;
