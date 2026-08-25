CREATE TABLE receivables (                                          -- RF-041
  id                      uuid PRIMARY KEY,
  charge_id               uuid NOT NULL REFERENCES charges(id),
  installment_number      int NOT NULL CHECK (installment_number >= 1),
  amount_cents            bigint NOT NULL CHECK (amount_cents > 0),
  -- [FIX-B6] parte do vendedor e do afiliado nesta parcela, já rateadas pelo
  -- método do maior resto. Gravadas, não recalculadas: o rateio é feito uma vez.
  seller_amount_cents     bigint NOT NULL CHECK (seller_amount_cents >= 0),
  affiliate_amount_cents  bigint NOT NULL DEFAULT 0 CHECK (affiliate_amount_cents >= 0),
  expected_at             timestamptz NOT NULL,
  settled_at              timestamptz,
  provider_receivable_id  text,
  UNIQUE (charge_id, installment_number),
  -- [FIX-D05] o rateio por parcela era gravado sem nenhuma amarração: uma
  -- parcela aceitava parte do vendedor maior que a própria parcela (H2).
  CHECK (seller_amount_cents + affiliate_amount_cents <= amount_cents)
);
CREATE INDEX ON receivables (expected_at) WHERE settled_at IS NULL;
