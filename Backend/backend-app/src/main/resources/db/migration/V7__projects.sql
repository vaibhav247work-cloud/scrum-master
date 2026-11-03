CREATE TABLE project (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    project_key VARCHAR(20) NOT NULL,
    name VARCHAR(240) NOT NULL,
    description VARCHAR(4000) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_by BINARY(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_project_tenant_key (tenant_id, project_key),
    KEY ix_project_tenant_status_created (tenant_id, status, created_at, id),
    CONSTRAINT fk_project_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_project_created_by FOREIGN KEY (created_by) REFERENCES tenant_user (id)
) ENGINE=InnoDB;
