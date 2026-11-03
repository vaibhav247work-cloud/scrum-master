CREATE TABLE in_app_notification (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    recipient_user_id BINARY(16) NOT NULL,
    source_event_id BINARY(16) NOT NULL,
    notification_type VARCHAR(120) NOT NULL,
    title VARCHAR(240) NOT NULL,
    body VARCHAR(2000) NOT NULL,
    read_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_notification_event_recipient (source_event_id, recipient_user_id, notification_type),
    KEY ix_notification_recipient (tenant_id, recipient_user_id, read_at, created_at, id),
    CONSTRAINT fk_notification_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_user_id) REFERENCES tenant_user (id),
    CONSTRAINT fk_notification_event FOREIGN KEY (source_event_id) REFERENCES outbox_event (event_id)
) ENGINE=InnoDB;
