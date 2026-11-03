ALTER TABLE tenant_user
    ADD UNIQUE KEY uq_tenant_user_tenant_id (tenant_id, id);

ALTER TABLE project
    ADD UNIQUE KEY uq_project_tenant_id (tenant_id, id);

CREATE TABLE sprint (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    project_id BINARY(16) NOT NULL,
    sprint_key VARCHAR(20) NOT NULL,
    name VARCHAR(240) NOT NULL,
    goal VARCHAR(4000) NOT NULL,
    start_date DATE NULL,
    end_date DATE NULL,
    status VARCHAR(32) NOT NULL,
    created_by BINARY(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_sprint_tenant_project_key (tenant_id, project_id, sprint_key),
    KEY ix_sprint_tenant_project_status (tenant_id, project_id, status, start_date, id),
    CONSTRAINT fk_sprint_project_tenant FOREIGN KEY (tenant_id, project_id)
        REFERENCES project (tenant_id, id),
    CONSTRAINT fk_sprint_created_by_tenant FOREIGN KEY (tenant_id, created_by)
        REFERENCES tenant_user (tenant_id, id),
    CONSTRAINT fk_sprint_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id)
) ENGINE=InnoDB;
