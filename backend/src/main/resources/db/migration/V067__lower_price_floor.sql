-- Piso de preço da oferta e piso técnico de cobrança passam a R$ 2,00.
ALTER TABLE offers DROP CONSTRAINT offers_amount_matches_pricing_mode;
ALTER TABLE offers ADD CONSTRAINT offers_amount_matches_pricing_mode CHECK (
  (pricing_mode = 'FIXED' AND amount_cents >= 200)
  OR
  (pricing_mode = 'CUSTOMER_DEFINED' AND charge_type = 'ONE_TIME' AND amount_cents >= 200)
);

DO $$
DECLARE c record;
BEGIN
  FOR c IN
    SELECT conname FROM pg_constraint
    WHERE conrelid = 'orders'::regclass AND contype = 'c'
      AND (pg_get_constraintdef(oid) LIKE '%gross_cents >= 2000%'
        OR pg_get_constraintdef(oid) LIKE '%paid_cents >= 500%')
  LOOP
    EXECUTE format('ALTER TABLE orders DROP CONSTRAINT %I', c.conname);
  END LOOP;
END $$;

ALTER TABLE orders ADD CONSTRAINT orders_gross_cents_min CHECK (gross_cents >= 200);
ALTER TABLE orders ADD CONSTRAINT orders_paid_cents_min CHECK (paid_cents >= 200);
