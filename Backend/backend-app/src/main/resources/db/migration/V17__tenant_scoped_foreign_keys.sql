-- Defense in depth for the shared-schema tenant boundary.
-- Application queries already scope these relationships by tenant_id. These
-- composite constraints also prevent direct or future code from linking a
-- record in one tenant to a user or role in another tenant.

ALTER TABLE tenant_role
    ADD UNIQUE KEY uq_tenant_role_tenant_id_v17 (tenant_id, id);

ALTER TABLE tenant_role_permission
    DROP FOREIGN KEY fk_tenant_role_permission_role,
    ADD CONSTRAINT fk_tenant_role_permission_role_tenant
        FOREIGN KEY (tenant_id, role_id) REFERENCES tenant_role (tenant_id, id);

ALTER TABLE tenant_user_role
    DROP FOREIGN KEY fk_tenant_user_role_user,
    DROP FOREIGN KEY fk_tenant_user_role_role,
    ADD CONSTRAINT fk_tenant_user_role_user_tenant
        FOREIGN KEY (tenant_id, user_id) REFERENCES tenant_user (tenant_id, id),
    ADD CONSTRAINT fk_tenant_user_role_role_tenant
        FOREIGN KEY (tenant_id, role_id) REFERENCES tenant_role (tenant_id, id);

ALTER TABLE tenant_user_invitation
    DROP FOREIGN KEY fk_tenant_invitation_user,
    DROP FOREIGN KEY fk_tenant_invitation_creator,
    ADD CONSTRAINT fk_tenant_invitation_user_tenant
        FOREIGN KEY (tenant_id, user_id) REFERENCES tenant_user (tenant_id, id),
    ADD CONSTRAINT fk_tenant_invitation_creator_tenant
        FOREIGN KEY (tenant_id, created_by) REFERENCES tenant_user (tenant_id, id);

ALTER TABLE in_app_notification
    DROP FOREIGN KEY fk_notification_recipient,
    ADD CONSTRAINT fk_notification_recipient_tenant
        FOREIGN KEY (tenant_id, recipient_user_id) REFERENCES tenant_user (tenant_id, id);

ALTER TABLE project
    DROP FOREIGN KEY fk_project_created_by,
    ADD CONSTRAINT fk_project_created_by_tenant
        FOREIGN KEY (tenant_id, created_by) REFERENCES tenant_user (tenant_id, id);
