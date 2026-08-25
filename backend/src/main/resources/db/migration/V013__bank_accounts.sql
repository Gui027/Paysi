CREATE TABLE bank_accounts (
  id              uuid PRIMARY KEY,
  account_id      uuid NOT NULL REFERENCES accounts(id),
  bank_code       text NOT NULL,
  branch          text NOT NULL,
  number_enc      bytea NOT NULL,
  number_last4    text NOT NULL,
  holder_tax_id   text NOT NULL,
  pix_key_enc     bytea,
  verified_at     timestamptz,
  archived_at     timestamptz,
  created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON bank_accounts (account_id) WHERE archived_at IS NULL;

-- [FIX-D3] RF-068 era comentário. Agora é restrição.
CREATE FUNCTION bank_account_holder_matches() RETURNS trigger AS $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM accounts a
                 WHERE a.id = NEW.account_id AND a.tax_id = NEW.holder_tax_id) THEN
    RAISE EXCEPTION 'Conta bancaria precisa ser do mesmo CPF/CNPJ do titular (RF-068)';
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_bank_holder BEFORE INSERT OR UPDATE ON bank_accounts
  FOR EACH ROW EXECUTE FUNCTION bank_account_holder_matches();
