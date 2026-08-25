CREATE TABLE ledger_checkpoints (
  account_id     uuid NOT NULL,
  bucket         text NOT NULL,
  up_to_entry_id bigint NOT NULL,
  balance_cents  bigint NOT NULL,
  updated_at     timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (account_id, bucket)
);

-- [FIX-B2] Consolidação sob o MESMO bloqueio consultivo usado por toda escrita
-- no razão daquela conta. Enquanto o bloqueio é mantido, não existe lançamento
-- em voo para essa conta — logo max(id) é fronteira segura.
--
-- Sem isto, bigserial atribui o id ANTES do commit e uma entrada com id menor
-- pode confirmar depois do checkpoint fechar. Ela nunca mais entra na soma:
-- nem no checkpoint, nem no "WHERE id > up_to_entry_id".
CREATE FUNCTION ledger_consolidate_account(p_account uuid) RETURNS void AS $$
BEGIN
  PERFORM pg_advisory_xact_lock(4210, hashtext(p_account::text));

  INSERT INTO ledger_checkpoints (account_id, bucket, up_to_entry_id, balance_cents, updated_at)
  SELECT e.account_id,
         e.bucket,
         max(e.id),
         COALESCE(SUM(CASE WHEN e.direction = 'CREDIT' THEN e.amount_cents
                           ELSE -e.amount_cents END), 0),
         now()
  FROM ledger_entries e
  WHERE e.account_id = p_account
  GROUP BY e.account_id, e.bucket
  ON CONFLICT (account_id, bucket) DO UPDATE
    SET up_to_entry_id = EXCLUDED.up_to_entry_id,
        balance_cents  = EXCLUDED.balance_cents,
        updated_at     = now();
END $$ LANGUAGE plpgsql;

-- Reconstrução total: o conserto padrão depois de qualquer incidente
-- (documento 3, §5.2, passo 7).
CREATE FUNCTION ledger_rebuild_checkpoints(p_account uuid DEFAULT NULL) RETURNS void AS $$
DECLARE a uuid;
BEGIN
  IF p_account IS NULL THEN
    FOR a IN SELECT DISTINCT account_id FROM ledger_entries LOOP
      PERFORM ledger_consolidate_account(a);
    END LOOP;
  ELSE
    PERFORM ledger_consolidate_account(p_account);
  END IF;
END $$ LANGUAGE plpgsql;
