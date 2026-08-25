CREATE TABLE outbox_events (
  id           uuid PRIMARY KEY,
  account_id   uuid NOT NULL,
  event_type   text NOT NULL,
  payload      jsonb NOT NULL,
  created_at   timestamptz NOT NULL DEFAULT now(),
  published_at timestamptz
);
CREATE INDEX ON outbox_events (created_at) WHERE published_at IS NULL;

CREATE TABLE webhook_deliveries (
  id           uuid PRIMARY KEY,
  event_id     uuid NOT NULL REFERENCES outbox_events(id),
  endpoint_id  uuid NOT NULL REFERENCES webhook_endpoints(id),
  attempt      int NOT NULL,
  status_code  int,
  error        text,
  next_retry_at timestamptz,
  created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON webhook_deliveries (next_retry_at) WHERE next_retry_at IS NOT NULL;
CREATE INDEX ON webhook_deliveries (event_id);
