ALTER TABLE tenant_user
    ADD COLUMN password_hash VARCHAR(255) NULL AFTER display_name,
    ADD COLUMN activated_at TIMESTAMP(6) NULL AFTER status,
    ADD COLUMN last_login_at TIMESTAMP(6) NULL AFTER activated_at,
    ADD COLUMN failed_login_count INT NOT NULL DEFAULT 0 AFTER last_login_at,
    ADD COLUMN locked_until TIMESTAMP(6) NULL AFTER failed_login_count;

CREATE INDEX ix_tenant_user_login ON tenant_user (tenant_id, email, status);
