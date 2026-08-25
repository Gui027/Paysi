CREATE TABLE coupons (                                              -- RF-027
  id              uuid PRIMARY KEY,
  seller_id       uuid NOT NULL REFERENCES accounts(id),
  code            citext NOT NULL,
  kind            text NOT NULL CHECK (kind IN ('PERCENT','FIXED')),
  value           int NOT NULL CHECK (value > 0),   -- bps se PERCENT, centavos se FIXED
  max_redemptions int CHECK (max_redemptions IS NULL OR max_redemptions > 0),
  max_per_buyer   int NOT NULL DEFAULT 1 CHECK (max_per_buyer >= 1),
  redeemed_count  int NOT NULL DEFAULT 0 CHECK (redeemed_count >= 0),
  expires_at      timestamptz,
  archived_at     timestamptz,
  created_at      timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT percent_range CHECK (kind <> 'PERCENT' OR value BETWEEN 1 AND 10000),
  -- [FIX-B5] rede de segurança: mesmo que o UPDATE condicional falhe,
  -- o banco não deixa o contador passar do teto.
  CONSTRAINT redemption_cap CHECK (max_redemptions IS NULL OR redeemed_count <= max_redemptions)
);
CREATE UNIQUE INDEX uq_coupons_seller_code ON coupons (seller_id, code) WHERE archived_at IS NULL;

CREATE TABLE coupon_offers (
  coupon_id uuid NOT NULL REFERENCES coupons(id),
  offer_id  uuid NOT NULL REFERENCES offers(id),
  PRIMARY KEY (coupon_id, offer_id)
);

-- [FIX-B5] Trilha de resgate: sustenta max_per_buyer, auditoria e estorno.
CREATE TABLE coupon_redemptions (
  coupon_id   uuid NOT NULL REFERENCES coupons(id),
  order_id    uuid NOT NULL,          -- FK adicionada em V008 (orders vem depois)
  buyer_id    uuid NOT NULL,
  amount_cents bigint NOT NULL CHECK (amount_cents > 0),
  redeemed_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (coupon_id, order_id)
);
CREATE INDEX ON coupon_redemptions (coupon_id, buyer_id);
