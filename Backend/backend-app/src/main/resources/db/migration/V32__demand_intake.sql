INSERT INTO feature_definition (feature_key, display_name, status) VALUES
    ('DEMAND_INTAKE', 'Demand and Intake', 'ACTIVE')
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), status = VALUES(status);

CREATE TABLE demand_item (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    demand_key VARCHAR(32) NOT NULL,
    title VARCHAR(240) NOT NULL,
    description VARCHAR(8000) NOT NULL,
    demand_type VARCHAR(16) NOT NULL,
    priority VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED',
    requested_by BINARY(16) NOT NULL,
    owner_user_id BINARY(16) NULL,
    decided_by BINARY(16) NULL,
    decision_note VARCHAR(2000) NOT NULL DEFAULT '',
    decided_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_demand_tenant_key (tenant_id, demand_key),
    KEY ix_demand_tenant_status_priority (tenant_id, status, priority, created_at, id),
    KEY ix_demand_tenant_requester (tenant_id, requested_by, created_at, id),
    CONSTRAINT chk_demand_type CHECK (demand_type IN ('CLIENT', 'INTERNAL', 'IDEA', 'INCIDENT')),
    CONSTRAINT chk_demand_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT chk_demand_status CHECK (status IN ('SUBMITTED', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'DEFERRED', 'CONVERTED')),
    CONSTRAINT fk_demand_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_demand_requester_tenant FOREIGN KEY (tenant_id, requested_by) REFERENCES tenant_user (tenant_id, id),
    CONSTRAINT fk_demand_owner_tenant FOREIGN KEY (tenant_id, owner_user_id) REFERENCES tenant_user (tenant_id, id),
    CONSTRAINT fk_demand_decider_tenant FOREIGN KEY (tenant_id, decided_by) REFERENCES tenant_user (tenant_id, id)
) ENGINE=InnoDB;

INSERT INTO permission_definition (permission_key, display_name, status) VALUES
    ('demand.create', 'Submit demand requests', 'ACTIVE'),
    ('demand.read', 'View demand requests', 'ACTIVE'),
    ('demand.approve', 'Review and decide demand requests', 'ACTIVE')
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), status = VALUES(status);

INSERT INTO tenant_role_permission (tenant_id, role_id, permission_key)
SELECT role.tenant_id, role.id, permission.permission_key
FROM tenant_role role
JOIN permission_definition permission
  ON permission.permission_key IN ('demand.create', 'demand.read', 'demand.approve')
WHERE role.role_key = 'COMPANY_ADMIN'
ON DUPLICATE KEY UPDATE permission_key = VALUES(permission_key);
