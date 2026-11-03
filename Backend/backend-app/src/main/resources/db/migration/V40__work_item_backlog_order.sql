ALTER TABLE work_item
    ADD COLUMN backlog_position BIGINT NOT NULL DEFAULT 0 AFTER acceptance_criteria,
    ADD KEY ix_work_item_tenant_scope_rank (tenant_id, project_id, sprint_id, backlog_position, created_at, id);
