INSERT INTO feature_definition (feature_key, display_name, status) VALUES
    ('PORTFOLIO_PLANNING', 'Portfolio, Program and Initiative Planning', 'ACTIVE')
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), status = VALUES(status);

INSERT INTO permission_definition (permission_key, display_name, status) VALUES
    ('portfolio.read', 'View portfolio planning hierarchy', 'ACTIVE'),
    ('portfolio.manage', 'Manage portfolios, programs and initiatives', 'ACTIVE')
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), status = VALUES(status);

CREATE TABLE portfolio (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    portfolio_key VARCHAR(48) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(4000) NOT NULL,
    status VARCHAR(32) NOT NULL,
    owner_user_id BINARY(16) NULL,
    created_by BINARY(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_portfolio_tenant_key (tenant_id, portfolio_key),
    KEY ix_portfolio_tenant_id (tenant_id, id),
    KEY ix_portfolio_tenant_status (tenant_id, status, updated_at, id),
    CONSTRAINT fk_portfolio_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_portfolio_owner_tenant FOREIGN KEY (tenant_id, owner_user_id) REFERENCES tenant_user (tenant_id, id),
    CONSTRAINT fk_portfolio_creator_tenant FOREIGN KEY (tenant_id, created_by) REFERENCES tenant_user (tenant_id, id)
) ENGINE=InnoDB;

CREATE TABLE portfolio_program (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    portfolio_id BINARY(16) NOT NULL,
    program_key VARCHAR(48) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(4000) NOT NULL,
    status VARCHAR(32) NOT NULL,
    owner_user_id BINARY(16) NULL,
    created_by BINARY(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_program_portfolio_key (tenant_id, portfolio_id, program_key),
    KEY ix_program_tenant_id (tenant_id, id),
    KEY ix_program_tenant_portfolio (tenant_id, portfolio_id, status, updated_at, id),
    CONSTRAINT fk_program_portfolio FOREIGN KEY (tenant_id, portfolio_id) REFERENCES portfolio (tenant_id, id),
    CONSTRAINT fk_program_owner_tenant FOREIGN KEY (tenant_id, owner_user_id) REFERENCES tenant_user (tenant_id, id),
    CONSTRAINT fk_program_creator_tenant FOREIGN KEY (tenant_id, created_by) REFERENCES tenant_user (tenant_id, id)
) ENGINE=InnoDB;

CREATE TABLE portfolio_initiative (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    portfolio_id BINARY(16) NOT NULL,
    program_id BINARY(16) NULL,
    product_id BINARY(16) NULL,
    initiative_key VARCHAR(48) NOT NULL,
    title VARCHAR(240) NOT NULL,
    description VARCHAR(4000) NOT NULL,
    priority VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    target_date DATE NULL,
    owner_user_id BINARY(16) NULL,
    created_by BINARY(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_initiative_portfolio_key (tenant_id, portfolio_id, initiative_key),
    KEY ix_initiative_tenant_scope (tenant_id, portfolio_id, program_id, status, target_date, id),
    CONSTRAINT fk_initiative_portfolio FOREIGN KEY (tenant_id, portfolio_id) REFERENCES portfolio (tenant_id, id),
    CONSTRAINT fk_initiative_program FOREIGN KEY (tenant_id, program_id) REFERENCES portfolio_program (tenant_id, id),
    CONSTRAINT fk_initiative_product FOREIGN KEY (tenant_id, product_id) REFERENCES tenant_product (tenant_id, id),
    CONSTRAINT fk_initiative_owner_tenant FOREIGN KEY (tenant_id, owner_user_id) REFERENCES tenant_user (tenant_id, id),
    CONSTRAINT fk_initiative_creator_tenant FOREIGN KEY (tenant_id, created_by) REFERENCES tenant_user (tenant_id, id)
) ENGINE=InnoDB;

INSERT INTO tenant_role_permission (tenant_id, role_id, permission_key)
SELECT r.tenant_id, r.id, p.permission_key
FROM tenant_role r CROSS JOIN permission_definition p
WHERE r.role_key = 'COMPANY_ADMIN' AND r.status = 'ACTIVE'
  AND p.permission_key IN ('portfolio.read', 'portfolio.manage')
ON DUPLICATE KEY UPDATE permission_key = VALUES(permission_key);
