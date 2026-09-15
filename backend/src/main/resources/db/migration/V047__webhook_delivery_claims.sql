ALTER TABLE outbox_events
  ADD COLUMN locked_at timestamptz,
  ADD COLUMN lock_token uuid;

ALTER TABLE webhook_deliveries
  ADD COLUMN retry_locked_at timestamptz,
  ADD COLUMN retry_lock_token uuid;

CREATE UNIQUE INDEX uq_webhook_delivery_attempt
  ON webhook_deliveries(event_id, endpoint_id, attempt);

CREATE INDEX ix_outbox_claimable
  ON outbox_events(created_at)
  WHERE published_at IS NULL;

CREATE INDEX ix_webhook_delivery_retry_claimable
  ON webhook_deliveries(next_retry_at)
  WHERE next_retry_at IS NOT NULL;
