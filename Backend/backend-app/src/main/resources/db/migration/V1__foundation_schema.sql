CREATE TABLE tenant (
    id BINARY(16) NOT NULL,
    tenant_key VARCHAR(80) NOT NULL,
    legal_name VARCHAR(240) NOT NULL,
    display_name VARCHAR(240) NOT NULL,
    status VARCHAR(32) NOT NULL,
    default_timezone VARCHAR(80) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_tenant_key (tenant_key),
    KEY ix_tenant_status (status)
) ENGINE=InnoDB;

CREATE TABLE tenant_user (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    email VARCHAR(320) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    status VARCHAR(32) NOT NULL,
    offboarded_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_tenant_user_email (tenant_id, email),
    KEY ix_tenant_user_status (tenant_id, status),
    CONSTRAINT fk_tenant_user_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id)
) ENGINE=InnoDB;

CREATE TABLE feature_definition (
    feature_key VARCHAR(100) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    status VARCHAR(32) NOT NULL,
    PRIMARY KEY (feature_key)
) ENGINE=InnoDB;

CREATE TABLE tenant_feature_entitlement (
    tenant_id BINARY(16) NOT NULL,
    feature_key VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    configuration_json JSON NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (tenant_id, feature_key),
    CONSTRAINT fk_tenant_feature_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_tenant_feature_definition FOREIGN KEY (feature_key) REFERENCES feature_definition (feature_key)
) ENGINE=InnoDB;

CREATE TABLE audit_event (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NULL,
    actor_id BINARY(16) NULL,
    event_type VARCHAR(120) NOT NULL,
    aggregate_type VARCHAR(120) NULL,
    aggregate_id BINARY(16) NULL,
    correlation_id VARCHAR(100) NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    payload_json JSON NULL,
    PRIMARY KEY (id),
    KEY ix_audit_tenant_time (tenant_id, occurred_at, id),
    KEY ix_audit_type_time (event_type, occurred_at, id)
) ENGINE=InnoDB;

CREATE TABLE outbox_event (
    event_id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    event_type VARCHAR(150) NOT NULL,
    event_version INT NOT NULL,
    aggregate_type VARCHAR(120) NOT NULL,
    aggregate_id BINARY(16) NOT NULL,
    actor_id BINARY(16) NULL,
    correlation_id VARCHAR(100) NULL,
    causation_id BINARY(16) NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    payload_json JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (event_id),
    KEY ix_outbox_tenant_created (tenant_id, created_at, event_id),
    KEY ix_outbox_type_created (event_type, created_at, event_id)
) ENGINE=InnoDB;

CREATE TABLE outbox_event_delivery (
    id BINARY(16) NOT NULL,
    event_id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    consumer VARCHAR(100) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NOT NULL,
    processed_at TIMESTAMP(6) NULL,
    last_error VARCHAR(2000) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_outbox_delivery_consumer (event_id, consumer),
    KEY ix_outbox_delivery_claim (tenant_id, status, next_attempt_at, id),
    CONSTRAINT fk_outbox_delivery_event FOREIGN KEY (event_id) REFERENCES outbox_event (event_id)
) ENGINE=InnoDB;
