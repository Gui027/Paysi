CREATE TABLE affiliations (
  id              uuid PRIMARY KEY,
  product_id      uuid NOT NULL REFERENCES products(id),
  affiliate_id    uuid NOT NULL REFERENCES accounts(id),
  commission_bps  int NOT NULL CHECK (commission_bps BETWEEN 0 AND 5000),
  recurring       boolean NOT NULL,
  status          text NOT NULL DEFAULT 'REQUESTED'
                    CHECK (status IN ('REQUESTED','APPROVED','REJECTED','ENDED')),
  ended_reason    text CHECK (ended_reason IN ('BY_SELLER','BY_AFFILIATE','FRAUD')),
  approved_at     timestamptz,
  ended_at        timestamptz,
  created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_affiliation_active ON affiliations (product_id, affiliate_id)
  WHERE status IN ('REQUESTED','APPROVED');

CREATE TABLE affiliate_clicks (
  id              uuid PRIMARY KEY,
  affiliation_id  uuid NOT NULL REFERENCES affiliations(id),
  product_id      uuid NOT NULL REFERENCES products(id),
  visitor_key     text NOT NULL,
  ip              inet,
  created_at      timestamptz NOT NULL DEFAULT now(),
  expires_at      timestamptz NOT NULL                              -- created_at + 60 dias
);
CREATE INDEX ON affiliate_clicks (visitor_key, product_id, created_at DESC);
CREATE INDEX ON affiliate_clicks (expires_at);
