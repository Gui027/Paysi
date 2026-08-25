-- [FIX-C3] RF-105 (reembolso parcial) não tinha entidade: o pedido só sabia
-- dizer REFUNDED. Cada reembolso é um objeto próprio, como em toda plataforma
-- de pagamento; o acumulador em charges/orders é a projeção.
CREATE TABLE refunds (
  id                 uuid PRIMARY KEY,
  charge_id          uuid NOT NULL REFERENCES charges(id),
  amount_cents       bigint NOT NULL CHECK (amount_cents > 0),
  -- [FIX-B8] repartição por parte, gravada em cada reembolso.
  -- Reembolso parcial é o TERCEIRO ponto de truncamento do sistema, e não
  -- tinha regra escrita. Cada parcial trunca proporcionalmente; o ÚLTIMO
  -- (o que completa o total) liquida exatamente o que resta da alocação
  -- original. Sem isso, a soma dos parciais não fecha com a venda.
  seller_cents       bigint NOT NULL CHECK (seller_cents >= 0),
  affiliate_cents    bigint NOT NULL DEFAULT 0 CHECK (affiliate_cents >= 0),
  platform_cents     bigint NOT NULL CHECK (platform_cents >= 0),
  provider_cents     bigint NOT NULL DEFAULT 0 CHECK (provider_cents >= 0),
  reason             text,
  status             text NOT NULL DEFAULT 'PENDING'
                       CHECK (status IN ('PENDING','SUCCEEDED','FAILED')),
  provider_refund_id text UNIQUE,
  idempotency_key    text NOT NULL,
  requested_by       text NOT NULL CHECK (requested_by IN ('BUYER','SELLER','ADMIN','SYSTEM')),
  created_at         timestamptz NOT NULL DEFAULT now(),
  settled_at         timestamptz,
  UNIQUE (charge_id, idempotency_key),
  CHECK (seller_cents + affiliate_cents + platform_cents + provider_cents = amount_cents)
);
CREATE INDEX ON refunds (charge_id);
