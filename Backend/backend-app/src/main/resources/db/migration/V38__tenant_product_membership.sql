INSERT INTO feature_definition (feature_key, display_name, status) VALUES
    ('PRODUCT_MANAGEMENT', 'Tenant Products and Product Access', 'ACTIVE')
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), status = VALUES(status);

INSERT INTO permission_definition (permission_key, display_name, status) VALUES
    ('product.read', 'View tenant products', 'ACTIVE'),
    ('product.manage', 'Manage tenant products and access', 'ACTIVE')
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), status = VALUES(status);

CREATE TABLE tenant_product (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    product_key VARCHAR(48) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(4000) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_by BINARY(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_tenant_product_key (tenant_id, product_key),
    KEY ix_tenant_product_tenant_id (tenant_id, id),
    KEY ix_tenant_product_status_name (tenant_id, status, name, id),
    CONSTRAINT fk_product_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_product_creator_tenant FOREIGN KEY (tenant_id, created_by) REFERENCES tenant_user (tenant_id, id)
) ENGINE=InnoDB;

CREATE TABLE tenant_product_feature (
    tenant_id BINARY(16) NOT NULL,
    product_id BINARY(16) NOT NULL,
    feature_key VARCHAR(100) NOT NULL,
    PRIMARY KEY (tenant_id, product_id, feature_key),
    CONSTRAINT fk_product_feature_product FOREIGN KEY (tenant_id, product_id) REFERENCES tenant_product (tenant_id, id),
    CONSTRAINT fk_product_feature_definition FOREIGN KEY (feature_key) REFERENCES feature_definition (feature_key)
) ENGINE=InnoDB;

CREATE TABLE tenant_product_member (
    tenant_id BINARY(16) NOT NULL,
    product_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    access_status VARCHAR(32) NOT NULL,
    product_role VARCHAR(80) NOT NULL,
    created_by BINARY(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (tenant_id, product_id, user_id),
    KEY ix_product_member_user_status (tenant_id, user_id, access_status, product_id),
    CONSTRAINT fk_product_member_product FOREIGN KEY (tenant_id, product_id) REFERENCES tenant_product (tenant_id, id),
    CONSTRAINT fk_product_member_user FOREIGN KEY (tenant_id, user_id) REFERENCES tenant_user (tenant_id, id),
    CONSTRAINT fk_product_member_creator FOREIGN KEY (tenant_id, created_by) REFERENCES tenant_user (tenant_id, id)
) ENGINE=InnoDB;

INSERT INTO tenant_role_permission (tenant_id, role_id, permission_key)
SELECT r.tenant_id, r.id, p.permission_key
FROM tenant_role r CROSS JOIN permission_definition p
WHERE r.role_key = 'COMPANY_ADMIN' AND r.status = 'ACTIVE'
  AND p.permission_key IN ('product.read', 'product.manage')
ON DUPLICATE KEY UPDATE permission_key = VALUES(permission_key);
