CREATE TABLE work_item_assignment_history (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    work_item_id BINARY(16) NOT NULL,
    previous_assignee_user_id BINARY(16) NULL,
    new_assignee_user_id BINARY(16) NULL,
    changed_by BINARY(16) NOT NULL,
    reason VARCHAR(1000) NOT NULL DEFAULT '',
    changed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_assignment_history_work_item (tenant_id, work_item_id, changed_at, id),
    KEY ix_assignment_history_user (tenant_id, new_assignee_user_id, changed_at, id),
    CONSTRAINT fk_assignment_history_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_assignment_history_work_item FOREIGN KEY (tenant_id, work_item_id)
        REFERENCES work_item (tenant_id, id),
    CONSTRAINT fk_assignment_history_previous_user FOREIGN KEY (tenant_id, previous_assignee_user_id)
        REFERENCES tenant_user (tenant_id, id),
    CONSTRAINT fk_assignment_history_new_user FOREIGN KEY (tenant_id, new_assignee_user_id)
        REFERENCES tenant_user (tenant_id, id),
    CONSTRAINT fk_assignment_history_changed_by FOREIGN KEY (tenant_id, changed_by)
        REFERENCES tenant_user (tenant_id, id)
) ENGINE=InnoDB;
