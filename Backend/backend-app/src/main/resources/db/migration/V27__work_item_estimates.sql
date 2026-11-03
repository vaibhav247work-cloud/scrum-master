ALTER TABLE work_item
    ADD COLUMN estimate_minutes INT NULL AFTER description,
    ADD CONSTRAINT chk_work_item_estimate_minutes CHECK (estimate_minutes IS NULL OR estimate_minutes BETWEEN 1 AND 10080),
    ADD KEY ix_work_item_tenant_project_estimate (tenant_id, project_id, status, estimate_minutes, id);
