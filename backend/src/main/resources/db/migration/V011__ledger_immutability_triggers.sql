CREATE FUNCTION ledger_is_append_only() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'ledger_entries e append-only. Corrija por lancamento inverso.';
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_no_update BEFORE UPDATE ON ledger_entries
  FOR EACH ROW EXECUTE FUNCTION ledger_is_append_only();
CREATE TRIGGER trg_ledger_no_delete BEFORE DELETE ON ledger_entries
  FOR EACH ROW EXECUTE FUNCTION ledger_is_append_only();

CREATE TRIGGER trg_ledger_tx_no_update BEFORE UPDATE ON ledger_transactions
  FOR EACH ROW EXECUTE FUNCTION ledger_is_append_only();
CREATE TRIGGER trg_ledger_tx_no_delete BEFORE DELETE ON ledger_transactions
  FOR EACH ROW EXECUTE FUNCTION ledger_is_append_only();

REVOKE UPDATE, DELETE ON ledger_entries      FROM paysi_app;
REVOKE UPDATE, DELETE ON ledger_transactions FROM paysi_app;

-- [FIX-A1] Integridade referencial que o modelo original não tinha:
-- account_id apontava para "conta de usuário ou de sistema", sem FK nenhuma.
CREATE FUNCTION ledger_entry_account_valid() RETURNS trigger AS $$
BEGIN
  IF NEW.bucket = 'SYSTEM' THEN
    IF NOT EXISTS (SELECT 1 FROM ledger_accounts WHERE id = NEW.account_id) THEN
      RAISE EXCEPTION 'bucket SYSTEM exige conta de sistema existente: %', NEW.account_id;
    END IF;
  ELSE
    IF NOT EXISTS (SELECT 1 FROM accounts WHERE id = NEW.account_id) THEN
      RAISE EXCEPTION 'bucket de usuario exige conta existente: %', NEW.account_id;
    END IF;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_account_valid BEFORE INSERT ON ledger_entries
  FOR EACH ROW EXECUTE FUNCTION ledger_entry_account_valid();

-- [FIX-D07] A projeção de agendamento passa a ser POPULADA PELO BANCO.
-- Antes era responsabilidade da aplicação lembrar de inserir a linha (item 20
-- da lista de revisão do doc 5). Esquecer significa dinheiro que nunca sai da
-- garantia — silenciosamente, para sempre. Um item de lista de revisão que o
-- banco pode garantir não deveria ser item de lista de revisão.
CREATE FUNCTION ledger_schedule_release() RETURNS trigger AS $$
BEGIN
  IF NEW.release_at IS NOT NULL THEN
    INSERT INTO ledger_release_schedule (entry_id, account_id, bucket, amount_cents, release_at)
    VALUES (NEW.id, NEW.account_id, NEW.bucket, NEW.amount_cents, NEW.release_at);
  END IF;
  RETURN NULL;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_schedule AFTER INSERT ON ledger_entries
  FOR EACH ROW EXECUTE FUNCTION ledger_schedule_release();

-- [FIX-D08] Não negatividade deixa de ser achado do dia seguinte e passa a ser
-- impossível. Gatilho de restrição DEFERIDO: roda no COMMIT, portanto a ordem
-- dos lançamentos dentro da transação não importa.
--
-- RNF-032 dizia "verificado por consulta diária". Consulta diária descobre que
-- o saldo ficou negativo ontem; o saque já saiu. As verificações 2 e 3 do §3.6
-- continuam existindo como rede — para o caso de alguém escrever no banco por
-- fora da aplicação.
CREATE FUNCTION ledger_assert_bucket_sign() RETURNS trigger AS $$
DECLARE v_saldo bigint;
BEGIN
  IF NEW.bucket = 'SYSTEM' THEN RETURN NULL; END IF;

  SELECT COALESCE(SUM(CASE WHEN direction = 'CREDIT' THEN amount_cents
                           ELSE -amount_cents END), 0)
    INTO v_saldo
    FROM ledger_entries
   WHERE account_id = NEW.account_id AND bucket = NEW.bucket;

  IF NEW.bucket = 'DEBT' THEN
    IF v_saldo > 0 THEN
      RAISE EXCEPTION 'DEBT positivo em % (saldo=%): compensacao maior que a divida (RF-070)',
        NEW.account_id, v_saldo;
    END IF;
  ELSIF v_saldo < 0 THEN
    RAISE EXCEPTION 'Bucket % negativo em % (saldo=%) (RNF-032)',
      NEW.bucket, NEW.account_id, v_saldo;
  END IF;
  RETURN NULL;
END $$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_ledger_bucket_sign
  AFTER INSERT ON ledger_entries
  DEFERRABLE INITIALLY DEFERRED
  FOR EACH ROW EXECUTE FUNCTION ledger_assert_bucket_sign();
