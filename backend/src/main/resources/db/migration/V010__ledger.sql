-- [FIX-A1] Contas de sistema passam a declarar o próprio saldo normal.
-- Sem isso a verificação de não negatividade acusa SYS_CLEARING todo dia.
CREATE TABLE ledger_accounts (
  id             uuid PRIMARY KEY,
  code           text NOT NULL UNIQUE,
  name           text NOT NULL,
  normal_balance text NOT NULL CHECK (normal_balance IN ('DEBIT','CREDIT')),
  created_at     timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE ledger_transactions (
  id             uuid PRIMARY KEY,
  type           text NOT NULL CHECK (type IN
                   ('SALE','GUARANTEE_RELEASE','RELEASE','RESERVE_RELEASE',
                    'REFUND','CHARGEBACK','CHARGEBACK_REVERSAL',
                    'PAYOUT','PAYOUT_REVERSAL','PLATFORM_FEE',
                    'DEBT_WRITEOFF','ADJUSTMENT','ANTICIPATION')),
  -- [FIX-B1] referência estruturada + chave natural única: a mesma notificação
  -- do provedor entregue duas vezes não gera duas transações de venda.
  reference_type text NOT NULL CHECK (reference_type IN
                   ('CHARGE','REFUND','DISPUTE','PAYOUT','RECEIVABLE',
                    'PLATFORM_SUB','VERIFICATION','DEBT_WRITEOFF','ADJUSTMENT')),
  reference_id   text NOT NULL,
  description    text NOT NULL,
  created_at     timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_ledger_tx_natural
  ON ledger_transactions (type, reference_type, reference_id);

CREATE TABLE ledger_entries (
  id             bigserial PRIMARY KEY,
  transaction_id uuid NOT NULL REFERENCES ledger_transactions(id),
  account_id     uuid NOT NULL,   -- accounts.id  OU  ledger_accounts.id (ver gatilho V011)
  bucket         text NOT NULL CHECK (bucket IN
                   ('GUARANTEE','PENDING','RESERVE','AVAILABLE','DEBT','SYSTEM')),
  direction      text NOT NULL CHECK (direction IN ('DEBIT','CREDIT')),
  amount_cents   bigint NOT NULL CHECK (amount_cents > 0),
  origin         text NOT NULL CHECK (origin IN ('SALE','COMMISSION','FEE','DEBT','OTHER')),
  release_at     timestamptz,
  created_at     timestamptz NOT NULL DEFAULT now(),
  -- [FIX-D06] release_at agenda a SAÍDA de um valor que entrou. Em um débito
  -- ele agendaria a liberação de dinheiro que já saiu do bucket. Era aceito.
  CONSTRAINT release_only_on_credit CHECK (release_at IS NULL OR direction = 'CREDIT'),
  CONSTRAINT release_never_on_system CHECK (release_at IS NULL OR bucket <> 'SYSTEM')
);
CREATE INDEX ON ledger_entries (account_id, bucket) INCLUDE (direction, amount_cents);
CREATE INDEX ON ledger_entries (transaction_id);

-- [FIX-G1] Projeção MUTÁVEL de agendamento.
-- ledger_entries é append-only, logo não há onde marcar "já liberado".
-- Sem esta tabela, o processo horário de saída da garantia move o MESMO
-- dinheiro a cada hora, indefinidamente. É derivada e reconstruível.
CREATE TABLE ledger_release_schedule (
  entry_id               bigint PRIMARY KEY REFERENCES ledger_entries(id),
  account_id             uuid NOT NULL,
  bucket                 text NOT NULL,
  amount_cents           bigint NOT NULL CHECK (amount_cents > 0),
  release_at             timestamptz NOT NULL,
  released_at            timestamptz,
  release_transaction_id uuid REFERENCES ledger_transactions(id)
);
CREATE INDEX ON ledger_release_schedule (release_at) WHERE released_at IS NULL;
CREATE INDEX ON ledger_release_schedule (account_id, bucket) WHERE released_at IS NULL;
