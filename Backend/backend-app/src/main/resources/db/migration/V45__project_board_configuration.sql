CREATE TABLE project_board_configuration (
    tenant_id BINARY(16) NOT NULL,
    project_id BINARY(16) NOT NULL,
    grouping_key VARCHAR(32) NOT NULL,
    swimlane_key VARCHAR(32) NOT NULL,
    card_fields_json JSON NOT NULL,
    wip_limits_json JSON NOT NULL,
    updated_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (tenant_id, project_id),
    KEY ix_board_config_tenant_updated (tenant_id, updated_at, project_id),
    CONSTRAINT fk_board_config_project_tenant FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, id),
    CONSTRAINT fk_board_config_updated_by_tenant FOREIGN KEY (tenant_id, updated_by) REFERENCES tenant_user (tenant_id, id)
) ENGINE=InnoDB;
