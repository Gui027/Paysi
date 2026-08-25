CREATE TABLE disputes (
  id                  uuid PRIMARY KEY,
  charge_id           uuid NOT NULL REFERENCES charges(id),
  -- [FIX-D10] REFUND saiu daqui. Desde que `refunds` passou a existir (V025),
  -- o mesmo reembolso podia ser gravado em duas entidades, com dois estados
  -- divergentes e nenhuma delas autoritativa (H5). Disputa é contestação.
  kind                text NOT NULL DEFAULT 'CHARGEBACK' CHECK (kind = 'CHARGEBACK'),
  reason              text,
  amount_cents        bigint NOT NULL CHECK (amount_cents > 0),
  acquirer_fee_cents  bigint NOT NULL DEFAULT 0 CHECK (acquirer_fee_cents >= 0),
  status              text NOT NULL
                        CHECK (status IN ('OPEN','DEFENDED','WON','LOST')),
  deadline_at         timestamptz,
  provider_dispute_id text UNIQUE,
  created_at          timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_disputes_open ON disputes (charge_id, kind)
  WHERE status NOT IN ('WON','LOST');

CREATE TABLE sale_evidence (
  charge_id           uuid PRIMARY KEY REFERENCES charges(id),
  ip                  inet,
  user_agent          text,
  device_key          text,
  terms_hash          text NOT NULL,
  terms_accepted_at   timestamptz NOT NULL,
  three_ds_result     text,                                         -- RF-074
  email_delivered_at  timestamptz,
  email_opened_at     timestamptz,
  access_log          jsonb NOT NULL DEFAULT '[]'
);
