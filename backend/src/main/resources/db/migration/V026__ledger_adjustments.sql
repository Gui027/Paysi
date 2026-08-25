-- [FIX-B10] ADJUSTMENT e DEBT_WRITEOFF eram tipos declarados no CHECK do
-- razão sem nada por trás: reference_id não apontava para lugar nenhum e a
-- aprovação exigida por RF-116 e pelo doc 4 §5 não tinha onde ser registrada.
CREATE TABLE ledger_adjustments (
  id            uuid PRIMARY KEY,
  kind          text NOT NULL CHECK (kind IN ('ADJUSTMENT','DEBT_WRITEOFF')),
  account_id    uuid NOT NULL REFERENCES accounts(id),
  bucket        text NOT NULL,
  direction     text NOT NULL CHECK (direction IN ('DEBIT','CREDIT')),
  amount_cents  bigint NOT NULL CHECK (amount_cents > 0),
  reason        text NOT NULL,
  requested_by  uuid NOT NULL REFERENCES admin_users(id),
  approved_by   uuid REFERENCES admin_users(id),
  auto_approved boolean NOT NULL DEFAULT false,
  approved_at   timestamptz,
  status        text NOT NULL DEFAULT 'PENDING_APPROVAL'
                  CHECK (status IN ('PENDING_APPROVAL','APPROVED','REJECTED','APPLIED')),
  created_at    timestamptz NOT NULL DEFAULT now(),
  -- Segregação do doc 4 §5, no banco: quem aprova não é quem pede.
  -- Abaixo do limiar de valor, auto_approved dispensa a segunda assinatura —
  -- que é o arranjo possível enquanto a equipe for de uma pessoa.
  CONSTRAINT segregacao_de_funcao CHECK (
    status <> 'APPROVED' OR auto_approved
    OR (approved_by IS NOT NULL AND approved_by <> requested_by)
  )
);
CREATE INDEX ON ledger_adjustments (status) WHERE status = 'PENDING_APPROVAL';
CREATE INDEX ON ledger_adjustments (account_id, created_at DESC);
