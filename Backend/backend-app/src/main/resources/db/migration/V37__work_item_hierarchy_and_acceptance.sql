ALTER TABLE work_item
    ADD COLUMN parent_work_item_id BINARY(16) NULL AFTER sprint_id,
    ADD COLUMN acceptance_criteria TEXT NULL AFTER description,
    ADD KEY ix_work_item_tenant_id (tenant_id, id),
    ADD KEY ix_work_item_tenant_parent (tenant_id, parent_work_item_id, created_at, id),
    ADD CONSTRAINT chk_work_item_not_self_parent CHECK (parent_work_item_id IS NULL OR parent_work_item_id <> id),
    ADD CONSTRAINT fk_work_item_parent_tenant FOREIGN KEY (tenant_id, parent_work_item_id)
        REFERENCES work_item (tenant_id, id);
