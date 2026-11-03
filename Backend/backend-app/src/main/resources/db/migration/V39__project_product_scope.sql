ALTER TABLE project
    ADD COLUMN product_id BINARY(16) NULL AFTER tenant_id,
    ADD KEY ix_project_tenant_product_status (tenant_id, product_id, status, id),
    ADD CONSTRAINT fk_project_product_tenant FOREIGN KEY (tenant_id, product_id) REFERENCES tenant_product (tenant_id, id);
