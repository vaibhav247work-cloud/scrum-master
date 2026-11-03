INSERT INTO feature_definition (feature_key, display_name, status) VALUES
    ('TELEGRAM_NOTIFICATIONS', 'Telegram Notifications', 'ACTIVE')
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), status = VALUES(status);

INSERT INTO tenant_feature_entitlement (tenant_id, feature_key, enabled, configuration_json, updated_at)
SELECT t.id, 'TELEGRAM_NOTIFICATIONS', FALSE, NULL, CURRENT_TIMESTAMP(6)
FROM tenant t
ON DUPLICATE KEY UPDATE feature_key = VALUES(feature_key);

INSERT INTO permission_definition (permission_key, display_name, status) VALUES
    ('notification.channel.read', 'View notification channels', 'ACTIVE'),
    ('notification.channel.manage', 'Manage notification channels', 'ACTIVE')
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), status = VALUES(status);

INSERT INTO tenant_role_permission (tenant_id, role_id, permission_key)
SELECT role.tenant_id, role.id, permission.permission_key
FROM tenant_role role
JOIN permission_definition permission
  ON permission.permission_key IN ('notification.channel.read', 'notification.channel.manage')
WHERE role.role_key = 'COMPANY_ADMIN'
ON DUPLICATE KEY UPDATE permission_key = VALUES(permission_key);

CREATE TABLE notification_channel_config (
    tenant_id BINARY(16) NOT NULL,
    channel_type VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL,
    bot_token_ciphertext TEXT NULL,
    chat_id VARCHAR(200) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (tenant_id, channel_type),
    CONSTRAINT fk_notification_channel_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id)
) ENGINE=InnoDB;
