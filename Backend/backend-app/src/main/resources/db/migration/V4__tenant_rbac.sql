CREATE TABLE permission_definition (
    permission_key VARCHAR(120) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    status VARCHAR(32) NOT NULL,
    PRIMARY KEY (permission_key)
) ENGINE=InnoDB;

CREATE TABLE tenant_role (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    role_key VARCHAR(100) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    system_role BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_tenant_role_key (tenant_id, role_key),
    KEY ix_tenant_role_status (tenant_id, status),
    CONSTRAINT fk_tenant_role_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id)
) ENGINE=InnoDB;

CREATE TABLE tenant_role_permission (
    tenant_id BINARY(16) NOT NULL,
    role_id BINARY(16) NOT NULL,
    permission_key VARCHAR(120) NOT NULL,
    PRIMARY KEY (tenant_id, role_id, permission_key),
    CONSTRAINT fk_tenant_role_permission_role FOREIGN KEY (role_id) REFERENCES tenant_role (id),
    CONSTRAINT fk_tenant_role_permission_definition FOREIGN KEY (permission_key) REFERENCES permission_definition (permission_key)
) ENGINE=InnoDB;

CREATE TABLE tenant_user_role (
    tenant_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    role_id BINARY(16) NOT NULL,
    assigned_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (tenant_id, user_id, role_id),
    KEY ix_tenant_user_role_user (tenant_id, user_id),
    CONSTRAINT fk_tenant_user_role_user FOREIGN KEY (user_id) REFERENCES tenant_user (id),
    CONSTRAINT fk_tenant_user_role_role FOREIGN KEY (role_id) REFERENCES tenant_role (id)
) ENGINE=InnoDB;

INSERT INTO permission_definition (permission_key, display_name, status) VALUES
    ('tenant.user.read', 'View tenant users', 'ACTIVE'),
    ('tenant.user.manage', 'Manage tenant users', 'ACTIVE'),
    ('tenant.settings.manage', 'Manage tenant settings', 'ACTIVE'),
    ('feature.manage', 'Manage feature entitlements', 'ACTIVE'),
    ('project.read', 'View projects', 'ACTIVE'),
    ('project.manage', 'Manage projects', 'ACTIVE'),
    ('sprint.read', 'View sprints', 'ACTIVE'),
    ('sprint.manage', 'Manage sprints', 'ACTIVE'),
    ('work.read', 'View work', 'ACTIVE'),
    ('work.manage', 'Manage work', 'ACTIVE'),
    ('time.read', 'View time tracking', 'ACTIVE'),
    ('time.manage', 'Manage time tracking', 'ACTIVE')
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    status = VALUES(status);
