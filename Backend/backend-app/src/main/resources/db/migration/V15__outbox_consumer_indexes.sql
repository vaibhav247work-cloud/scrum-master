-- Delivery is independently retried per consumer. Keep both ready and stale-lease scans bounded.
CREATE INDEX ix_outbox_delivery_consumer_retry
    ON outbox_event_delivery (consumer, status, next_attempt_at, id);

CREATE INDEX ix_outbox_delivery_consumer_stale
    ON outbox_event_delivery (consumer, status, updated_at, id);
