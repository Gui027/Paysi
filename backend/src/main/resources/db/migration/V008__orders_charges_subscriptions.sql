CREATE TABLE orders (
  id              uuid PRIMARY KEY,
  offer_id        uuid NOT NULL REFERENCES offers(id),
  buyer_id        uuid NOT NULL REFERENCES buyers(id),
  affiliation_id  uuid REFERENCES affiliations(id),
  buyer_snapshot  jsonb NOT NULL,
  gross_cents     bigint NOT NULL CHECK (gross_cents >= 2000),
  discount_cents  bigint NOT NULL DEFAULT 0 CHECK (discount_cents >= 0),
  coupon_id       uuid REFERENCES coupons(id),
  paid_cents      bigint NOT NULL,
  method          text NOT NULL CHECK (method IN ('PIX','CARD','BOLETO')),
  installments    int NOT NULL DEFAULT 1 CHECK (installments BETWEEN 1 AND 12),
  -- [FIX-D04] o pedido carrega o CICLO DE VIDA DA COMPRA; o dinheiro (e portanto
  -- reembolso, contestação e plano aplicado) vive na COBRANÇA.
  --
  -- Com o acumulador aqui, uma assinatura de doze ciclos tem doze cobranças
  -- apontando para UM pedido cujo paid_cents é o do primeiro ciclo. Reembolsar
  -- dois ciclos de R$ 100 acumularia 20000 contra um teto de 10000 e a
  -- transação abortaria — reembolso legítimo recusado pelo banco. Reproduzido
  -- em teste (H1) antes da correção.
  --
  -- O estado consolidado do pedido passa a ser a visão v_order_status (V028).
  status          text NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING','PAID','FAILED','EXPIRED')),
  idempotency_key text NOT NULL,
  request_hash    text NOT NULL,       -- mesma chave + corpo diferente => 409
  confirmed_at    timestamptz,         -- [FIX-B3] marco do fato gerador contábil
  created_at      timestamptz NOT NULL DEFAULT now(),

  CHECK (paid_cents = gross_cents - discount_cents),
  -- [FIX-A2] piso técnico da cobrança (R$ 5,00), separado do piso comercial
  -- da oferta (R$ 20,00, RF-011). Cupom pode descer até aqui e não abaixo.
  CHECK (paid_cents >= 500)
);
CREATE UNIQUE INDEX uq_orders_idem ON orders (offer_id, idempotency_key);
CREATE INDEX ON orders (buyer_id);
CREATE INDEX ON orders (offer_id, created_at DESC);

ALTER TABLE coupon_redemptions
  ADD CONSTRAINT fk_coupon_redemption_order FOREIGN KEY (order_id) REFERENCES orders(id),
  ADD CONSTRAINT fk_coupon_redemption_buyer FOREIGN KEY (buyer_id) REFERENCES buyers(id);

CREATE TABLE subscriptions (
  id              uuid PRIMARY KEY,
  order_id        uuid NOT NULL REFERENCES orders(id),
  offer_id        uuid NOT NULL REFERENCES offers(id),
  affiliation_id  uuid REFERENCES affiliations(id),
  status          text NOT NULL DEFAULT 'TRIAL'
                    CHECK (status IN ('TRIAL','ACTIVE','PAST_DUE','CANCELED')),
  cycle_number    int NOT NULL DEFAULT 0,
  trial_ends_at   timestamptz,
  next_charge_at  timestamptz,
  canceled_at     timestamptz,
  provider_token  text,
  created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON subscriptions (next_charge_at) WHERE status IN ('ACTIVE','PAST_DUE');

CREATE TABLE charges (
  id                      uuid PRIMARY KEY,
  order_id                uuid NOT NULL REFERENCES orders(id),
  subscription_id         uuid REFERENCES subscriptions(id),
  cycle_number            int NOT NULL DEFAULT 1,
  amount_cents            bigint NOT NULL CHECK (amount_cents > 0),

  -- [FIX-C4] memória de cálculo congelada: reconciliação deixa de depender
  -- da tabela de preços vigente hoje.
  plan                    text NOT NULL CHECK (plan IN ('TRANSACIONAL','ESCALA')),
  platform_fee_bps        int NOT NULL CHECK (platform_fee_bps >= 0),
  platform_fee_fixed_cents bigint NOT NULL CHECK (platform_fee_fixed_cents >= 0),
  platform_fee_cents      bigint NOT NULL CHECK (platform_fee_cents >= 0),  -- taxa cobrada do vendedor
  affiliate_fee_cents     bigint NOT NULL DEFAULT 0 CHECK (affiliate_fee_cents >= 0),
  seller_amount_cents     bigint NOT NULL CHECK (seller_amount_cents >= 0),
  provider_fee_cents      bigint,                                  -- REAL, informado pelo provedor

  status                  text NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN ('PENDING','PAID','FAILED','EXPIRED',
                                              'PARTIALLY_REFUNDED','REFUNDED','CHARGEBACK')),
  refunded_cents          bigint NOT NULL DEFAULT 0 CHECK (refunded_cents >= 0),
  provider_charge_id      text UNIQUE,
  three_ds_result         text CHECK (three_ds_result IN
                            ('NOT_APPLICABLE','AUTHENTICATED','ATTEMPTED','FAILED')),  -- RF-100
  attempt_count           int NOT NULL DEFAULT 1,
  next_retry_at           timestamptz,
  paid_at                 timestamptz,
  confirmed_at            timestamptz,                             -- [FIX-B3]
  created_at              timestamptz NOT NULL DEFAULT now(),

  CHECK (refunded_cents <= amount_cents),
  -- invariante do doc 1 §3.4, verificada também no banco
  CHECK (seller_amount_cents + affiliate_fee_cents + platform_fee_cents = amount_cents)
);
CREATE INDEX ON charges (order_id);
CREATE INDEX ON charges (next_retry_at) WHERE status = 'FAILED' AND next_retry_at IS NOT NULL;
-- [FIX-B9] UNIQUE, não índice comum. A cobrança de assinatura é o único
-- caminho que movimenta dinheiro SEM chave de idempotência vinda do cliente
-- — não existe navegador para gerar uma. (subscription_id, cycle_number) é a
-- chave natural, e sem unicidade uma retentativa do processo de 15 minutos
-- cobra o mesmo ciclo duas vezes. ShedLock estreita a janela; não a fecha.
CREATE UNIQUE INDEX uq_charges_subscription_cycle
  ON charges (subscription_id, cycle_number) WHERE subscription_id IS NOT NULL;
