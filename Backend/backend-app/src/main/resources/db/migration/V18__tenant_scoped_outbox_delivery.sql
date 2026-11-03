-- Keep delivery records inside the same tenant boundary as their source event.
-- The existing event_id foreign key guarantees existence; this composite key
-- additionally guarantees that tenant_id and event_id belong together.
ALTER TABLE outbox_event
    ADD UNIQUE KEY uq_outbox_event_tenant_id (tenant_id, event_id);

ALTER TABLE outbox_event_delivery
    DROP FOREIGN KEY fk_outbox_delivery_event,
    ADD CONSTRAINT fk_outbox_delivery_event_tenant
        FOREIGN KEY (tenant_id, event_id) REFERENCES outbox_event (tenant_id, event_id);
