ALTER TABLE sprint
    ADD COLUMN completed_at TIMESTAMP(6) NULL AFTER updated_at,
    ADD COLUMN completed_by BINARY(16) NULL AFTER completed_at,
    ADD COLUMN completion_note VARCHAR(4000) NOT NULL DEFAULT '' AFTER completed_by,
    ADD KEY ix_sprint_tenant_status_completion (tenant_id, status, completed_at, id),
    ADD CONSTRAINT fk_sprint_completed_by_tenant FOREIGN KEY (tenant_id, completed_by)
        REFERENCES tenant_user (tenant_id, id);
