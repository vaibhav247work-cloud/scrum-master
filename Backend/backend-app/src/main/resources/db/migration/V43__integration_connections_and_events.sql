INSERT INTO permission_definition (permission_key, display_name, status) VALUES
    ('integration.connection.read', 'View integration connections', 'ACTIVE'),
    ('integration.connection.manage', 'Manage integration connections', 'ACTIVE'),
    ('integration.event.ingest', 'Ingest integration events', 'ACTIVE')
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), status = VALUES(status);

CREATE TABLE integration_connection (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    connection_type VARCHAR(32) NOT NULL,
    name VARCHAR(200) NOT NULL,
    status VARCHAR(32) NOT NULL,
    base_url VARCHAR(1000) NOT NULL,
    external_account VARCHAR(240) NOT NULL,
    secret_ciphertext TEXT NULL,
    last_error VARCHAR(2000) NOT NULL,
    created_by BINARY(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_integration_tenant_id (tenant_id, id),
    UNIQUE KEY uq_integration_tenant_name (tenant_id, name),
    KEY ix_integration_tenant_status (tenant_id, status, connection_type, id),
    CONSTRAINT fk_integration_connection_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_integration_connection_creator FOREIGN KEY (tenant_id, created_by) REFERENCES tenant_user (tenant_id, id)
) ENGINE=InnoDB;

CREATE TABLE external_integration_event (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    connection_id BINARY(16) NOT NULL,
    source_system VARCHAR(32) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    external_id VARCHAR(240) NOT NULL,
    repository VARCHAR(500) NOT NULL,
    branch VARCHAR(500) NOT NULL,
    environment VARCHAR(200) NOT NULL,
    source_url VARCHAR(2000) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    received_at TIMESTAMP(6) NOT NULL,
    status VARCHAR(32) NOT NULL,
    payload_json JSON NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_external_event_identity (tenant_id, connection_id, event_type, external_id),
    KEY ix_external_event_tenant_time (tenant_id, occurred_at, id),
    KEY ix_external_event_connection_status (tenant_id, connection_id, status, received_at, id),
    CONSTRAINT fk_external_event_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_external_event_connection FOREIGN KEY (tenant_id, connection_id) REFERENCES integration_connection (tenant_id, id)
) ENGINE=InnoDB;

INSERT INTO tenant_role_permission (tenant_id, role_id, permission_key)
SELECT r.tenant_id, r.id, p.permission_key
FROM tenant_role r CROSS JOIN permission_definition p
WHERE r.role_key = 'COMPANY_ADMIN' AND r.status = 'ACTIVE'
  AND p.permission_key IN ('integration.connection.read', 'integration.connection.manage', 'integration.event.ingest')
ON DUPLICATE KEY UPDATE permission_key = VALUES(permission_key);
