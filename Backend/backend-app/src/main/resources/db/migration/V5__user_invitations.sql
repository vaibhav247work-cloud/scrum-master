CREATE TABLE tenant_user_invitation (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    email VARCHAR(320) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    consumed_at TIMESTAMP(6) NULL,
    created_by BINARY(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_tenant_invitation_token (token_hash),
    KEY ix_tenant_invitation_pending (tenant_id, user_id, consumed_at, expires_at),
    CONSTRAINT fk_tenant_invitation_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_tenant_invitation_user FOREIGN KEY (user_id) REFERENCES tenant_user (id),
    CONSTRAINT fk_tenant_invitation_creator FOREIGN KEY (created_by) REFERENCES tenant_user (id)
) ENGINE=InnoDB;
