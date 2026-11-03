INSERT INTO feature_definition (feature_key, display_name, status) VALUES
    ('MIGRATION_IMPORT', 'Migration and Work Item Import', 'ACTIVE')
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), status = VALUES(status);

INSERT INTO permission_definition (permission_key, display_name, status) VALUES
    ('migration.read', 'View migration and import jobs', 'ACTIVE'),
    ('migration.execute', 'Execute tenant data imports', 'ACTIVE')
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), status = VALUES(status);

CREATE TABLE migration_import_job (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    job_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_rows INT NOT NULL,
    processed_rows INT NOT NULL DEFAULT 0,
    accepted_rows INT NOT NULL DEFAULT 0,
    rejected_rows INT NOT NULL DEFAULT 0,
    error_count INT NOT NULL DEFAULT 0,
    failure_reason VARCHAR(2000) NOT NULL,
    requested_by BINARY(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_import_job_tenant_id (tenant_id, id),
    KEY ix_import_job_tenant_status (tenant_id, status, updated_at, id),
    KEY ix_import_job_tenant_created (tenant_id, created_at, id),
    CONSTRAINT fk_import_job_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_import_job_requester FOREIGN KEY (tenant_id, requested_by) REFERENCES tenant_user (tenant_id, id)
) ENGINE=InnoDB;

CREATE TABLE migration_import_error (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    job_id BINARY(16) NOT NULL,
    `row_number` INT NOT NULL,
    source_key VARCHAR(100) NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    error_code VARCHAR(64) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_import_error_tenant_job (tenant_id, job_id, `row_number`, id),
    CONSTRAINT fk_import_error_job FOREIGN KEY (tenant_id, job_id) REFERENCES migration_import_job (tenant_id, id)
) ENGINE=InnoDB;

INSERT INTO tenant_role_permission (tenant_id, role_id, permission_key)
SELECT r.tenant_id, r.id, p.permission_key
FROM tenant_role r CROSS JOIN permission_definition p
WHERE r.role_key = 'COMPANY_ADMIN' AND r.status = 'ACTIVE'
  AND p.permission_key IN ('migration.read', 'migration.execute')
ON DUPLICATE KEY UPDATE permission_key = VALUES(permission_key);
