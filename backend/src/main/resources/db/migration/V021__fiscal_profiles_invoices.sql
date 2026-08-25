CREATE TABLE fiscal_profiles (                                      -- RF-095
  account_id        uuid PRIMARY KEY REFERENCES accounts(id),
  municipality_code text NOT NULL,
  municipal_reg     text,
  service_code      text NOT NULL,
  iss_bps           int NOT NULL CHECK (iss_bps BETWEEN 0 AND 500),
  tax_regime        text NOT NULL CHECK (tax_regime IN ('SIMPLES','PRESUMIDO','REAL')),
  credential_ref    text NOT NULL,                                  -- referência no cofre
  validated_at      timestamptz,
  created_at        timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE invoices (
  id             uuid PRIMARY KEY,
  charge_id      uuid NOT NULL REFERENCES charges(id),
  issuer_id      uuid NOT NULL REFERENCES accounts(id),             -- o VENDEDOR emite
  status         text NOT NULL DEFAULT 'QUEUED'
                   CHECK (status IN ('QUEUED','ISSUED','FAILED','CANCEL_REQUESTED',
                                     'CANCELED','CANCEL_FAILED')),
  provider_ref   text,
  number         text,
  pdf_url        text,
  amount_cents   bigint NOT NULL CHECK (amount_cents > 0),
  error          text,
  attempt_count  int NOT NULL DEFAULT 0,
  next_retry_at  timestamptz,
  issued_at      timestamptz,
  created_at     timestamptz NOT NULL DEFAULT now()
);
-- [FIX-D11] issuer_id era um uuid livre apontando para accounts. A nota podia
-- sair em nome de qualquer conta — inclusive a do afiliado (H11). Quem presta
-- o serviço é o vendedor do produto, e é ele quem responde pelo ISS (PEN-19).
CREATE FUNCTION invoice_issuer_is_seller() RETURNS trigger AS $$
DECLARE v_seller uuid;
BEGIN
  SELECT p.seller_id INTO v_seller
    FROM charges c
    JOIN orders   o ON o.id = c.order_id
    JOIN offers   f ON f.id = o.offer_id
    JOIN products p ON p.id = f.product_id
   WHERE c.id = NEW.charge_id;

  IF v_seller IS DISTINCT FROM NEW.issuer_id THEN
    RAISE EXCEPTION 'NFS-e precisa ser emitida em nome do vendedor do produto (%), nao de %',
      v_seller, NEW.issuer_id;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_invoice_issuer BEFORE INSERT OR UPDATE ON invoices
  FOR EACH ROW EXECUTE FUNCTION invoice_issuer_is_seller();

CREATE UNIQUE INDEX uq_invoice_per_charge ON invoices (charge_id) WHERE status <> 'FAILED';
CREATE INDEX ON invoices (next_retry_at) WHERE status IN ('QUEUED','CANCEL_REQUESTED');
