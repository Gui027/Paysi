-- [FIX-C4] Fonte única do plano comercial da conta.
CREATE TABLE platform_subscriptions (                               -- RF-102
  account_id            uuid PRIMARY KEY REFERENCES accounts(id),
  plan                  text NOT NULL DEFAULT 'TRANSACIONAL'
                          CHECK (plan IN ('TRANSACIONAL','ESCALA')),
  price_cents           bigint NOT NULL DEFAULT 0 CHECK (price_cents >= 0),
  current_period_start  timestamptz NOT NULL,
  current_period_end    timestamptz NOT NULL,
  status                text NOT NULL DEFAULT 'ACTIVE'
                          CHECK (status IN ('ACTIVE','PAST_DUE','DOWNGRADED')),
  past_due_since        timestamptz,
  CHECK (current_period_end > current_period_start)
);

-- [FIX-D12] FIX-C4 tirou `plan` de accounts e elegeu platform_subscriptions
-- como fonte única — mas nada garantia que a linha existisse. Sete das oito
-- contas da massa de teste ficaram sem plano (H10), e uma conta sem plano é
-- uma cobrança sem tabela de preço. O padrão nasce com a conta.
CREATE FUNCTION account_default_plan() RETURNS trigger AS $$
BEGIN
  INSERT INTO platform_subscriptions
    (account_id, plan, price_cents, current_period_start, current_period_end)
  VALUES (NEW.id, 'TRANSACIONAL', 0, now(), now() + interval '1 month')
  ON CONFLICT (account_id) DO NOTHING;
  RETURN NULL;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_account_default_plan AFTER INSERT ON accounts
  FOR EACH ROW EXECUTE FUNCTION account_default_plan();

CREATE TABLE plan_changes (                                         -- RF-114
  id          uuid PRIMARY KEY,
  account_id  uuid NOT NULL REFERENCES accounts(id),
  from_plan   text,
  to_plan     text NOT NULL,
  changed_by  uuid,                     -- accounts.id ou admin_users.id
  price_table text NOT NULL,            -- identificador da tabela vigente
  created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON plan_changes (account_id, created_at DESC);
