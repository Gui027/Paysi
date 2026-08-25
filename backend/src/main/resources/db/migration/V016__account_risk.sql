CREATE TABLE account_risk (
  account_id        uuid PRIMARY KEY REFERENCES accounts(id),
  volume_30d_cents  bigint NOT NULL DEFAULT 0,
  chargeback_bps    int NOT NULL DEFAULT 0,
  refund_bps        int NOT NULL DEFAULT 0,
  tier              int NOT NULL DEFAULT 0 CHECK (tier BETWEEN 0 AND 3),
  tier_changed_at   timestamptz,
  computed_at       timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE risk_events (
  id            uuid PRIMARY KEY,
  account_id    uuid NOT NULL REFERENCES accounts(id),
  kind          text NOT NULL CHECK (kind IN
                  ('ALERT','LIMIT','SUSPEND','TIER_UP','TIER_DOWN','DEBT_REVIEW')),
  metric        text,
  value_bps     int,
  threshold_bps int,
  reason        text NOT NULL,                                      -- RF-078
  notified_at   timestamptz,
  created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON risk_events (account_id, created_at DESC);

-- Índice agregado da plataforma (RF-106), materializado pelo job diário.
CREATE TABLE platform_risk_index (
  computed_on     date PRIMARY KEY,
  chargeback_bps  int NOT NULL,
  refund_bps      int NOT NULL,
  volume_cents    bigint NOT NULL,
  computed_at     timestamptz NOT NULL DEFAULT now()
);
