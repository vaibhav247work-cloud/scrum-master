CREATE TABLE tenant_forecast_policy (
    tenant_id BINARY(16) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    release_at_risk_days INT NOT NULL DEFAULT 7,
    release_at_risk_completion_percent INT NOT NULL DEFAULT 80,
    project_over_plan_percent INT NOT NULL DEFAULT 100,
    updated_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (tenant_id),
    KEY ix_forecast_policy_updated_by (tenant_id, updated_by),
    CONSTRAINT chk_forecast_policy_days CHECK (release_at_risk_days BETWEEN 0 AND 365),
    CONSTRAINT chk_forecast_policy_completion CHECK (release_at_risk_completion_percent BETWEEN 0 AND 100),
    CONSTRAINT chk_forecast_policy_over_plan CHECK (project_over_plan_percent BETWEEN 1 AND 500),
    CONSTRAINT fk_forecast_policy_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_forecast_policy_updated_by FOREIGN KEY (tenant_id, updated_by)
        REFERENCES tenant_user (tenant_id, id)
) ENGINE=InnoDB;
