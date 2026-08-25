CREATE TABLE payouts (
  id                  uuid PRIMARY KEY,
  account_id          uuid NOT NULL REFERENCES accounts(id),
  amount_cents        bigint NOT NULL CHECK (amount_cents >= 200),
  bank_account_id     uuid NOT NULL REFERENCES bank_accounts(id),
  status              text NOT NULL DEFAULT 'REQUESTED'
                        CHECK (status IN ('REQUESTED','SENT','CONFIRMED','FAILED')),
  provider_transfer_id text,
  idempotency_key     text NOT NULL,
  created_at          timestamptz NOT NULL DEFAULT now()
);
-- [FIX] escopo por conta, não global (mesma razão do índice de orders)
CREATE UNIQUE INDEX uq_payouts_idem ON payouts (account_id, idempotency_key);
CREATE INDEX ON payouts (account_id, created_at DESC);

-- [FIX-D09] O defeito mais grave encontrado nesta passada: nada ligava
-- payouts.account_id a bank_accounts.account_id. Um saque da conta A para a
-- conta bancária de B era aceito pelo banco — dinheiro saindo para o titular
-- errado, que é a classe AM-12 (acesso a recurso de outra conta) no caminho
-- onde ela custa mais caro. Verificado antes da correção: aceito (H9).
CREATE FUNCTION payout_bank_account_belongs() RETURNS trigger AS $$
DECLARE v_owner uuid; v_verified timestamptz; v_archived timestamptz;
BEGIN
  SELECT account_id, verified_at, archived_at
    INTO v_owner, v_verified, v_archived
    FROM bank_accounts WHERE id = NEW.bank_account_id;

  IF v_owner IS DISTINCT FROM NEW.account_id THEN
    RAISE EXCEPTION 'Conta bancaria % nao pertence a conta % (RF-068)',
      NEW.bank_account_id, NEW.account_id;
  END IF;
  IF v_verified IS NULL THEN
    RAISE EXCEPTION 'Conta bancaria % nao verificada', NEW.bank_account_id;
  END IF;
  IF v_archived IS NOT NULL THEN
    RAISE EXCEPTION 'Conta bancaria % arquivada', NEW.bank_account_id;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_payout_bank_owner BEFORE INSERT OR UPDATE ON payouts
  FOR EACH ROW EXECUTE FUNCTION payout_bank_account_belongs();
