ALTER TABLE work_item
    ADD COLUMN completed_at TIMESTAMP(6) NULL AFTER updated_at,
    ADD KEY ix_work_item_tenant_project_completed (tenant_id, project_id, status, completed_at, id);

ALTER TABLE tenant_forecast_policy
    ADD COLUMN historical_calibration_enabled BOOLEAN NOT NULL DEFAULT FALSE AFTER enabled;
