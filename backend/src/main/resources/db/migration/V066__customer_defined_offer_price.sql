ALTER TABLE offers
  ADD COLUMN pricing_mode text NOT NULL DEFAULT 'FIXED'
    CHECK (pricing_mode IN ('FIXED', 'CUSTOMER_DEFINED'));

ALTER TABLE offers DROP CONSTRAINT offers_amount_cents_check;

ALTER TABLE offers ADD CONSTRAINT offers_amount_matches_pricing_mode CHECK (
  (pricing_mode = 'FIXED' AND amount_cents >= 2000)
  OR
  (pricing_mode = 'CUSTOMER_DEFINED' AND charge_type = 'ONE_TIME' AND amount_cents >= 500)
);

COMMENT ON COLUMN offers.amount_cents IS
  'Preço da oferta FIXED ou valor mínimo aceito quando pricing_mode=CUSTOMER_DEFINED';
