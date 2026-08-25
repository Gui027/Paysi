CREATE TABLE webhook_endpoints (                                    -- RF-109
  id                uuid PRIMARY KEY,
  account_id        uuid NOT NULL REFERENCES accounts(id),
  url               text NOT NULL,
  secret_enc        bytea NOT NULL,
  secret_prev_enc   bytea,
  secret_rotated_at timestamptz,
  event_types       text[] NOT NULL DEFAULT '{}',
  disabled_at       timestamptz,
  created_at        timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON webhook_endpoints (account_id) WHERE disabled_at IS NULL;
