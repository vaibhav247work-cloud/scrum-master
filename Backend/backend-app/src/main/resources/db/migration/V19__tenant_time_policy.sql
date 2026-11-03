CREATE TABLE tenant_time_policy (
    tenant_id BINARY(16) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    expected_daily_minutes INT NOT NULL DEFAULT 480,
    allow_overtime BOOLEAN NOT NULL DEFAULT FALSE,
    allow_weekend BOOLEAN NOT NULL DEFAULT FALSE,
    rounding_increment_minutes SMALLINT NOT NULL DEFAULT 1,
    rounding_mode VARCHAR(16) NOT NULL DEFAULT 'NEAREST',
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (tenant_id),
    CONSTRAINT chk_time_policy_daily_minutes CHECK (expected_daily_minutes BETWEEN 1 AND 1440),
    CONSTRAINT chk_time_policy_rounding_increment CHECK (rounding_increment_minutes IN (1, 5, 10, 15, 30)),
    CONSTRAINT chk_time_policy_rounding_mode CHECK (rounding_mode IN ('NEAREST', 'UP', 'DOWN')),
    CONSTRAINT fk_time_policy_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id)
) ENGINE=InnoDB;
