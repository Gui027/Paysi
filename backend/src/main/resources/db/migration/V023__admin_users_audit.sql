CREATE TABLE admin_users (
  id            uuid PRIMARY KEY,
  email         citext NOT NULL UNIQUE,
  password_hash text NOT NULL,
  role          text NOT NULL CHECK (role IN ('SUPPORT','RISK','COMPLIANCE','ADMIN')),
  mfa_enforced  boolean NOT NULL DEFAULT true,                      -- RNF-022
  disabled_at   timestamptz,
  created_at    timestamptz NOT NULL DEFAULT now()
);

-- [FIX-A4] mfa_credentials referencia accounts(id). Operador interno não é
-- conta de usuário, logo não tinha onde guardar o segundo fator — e o item 19
-- da lista de lançamento é bloqueante.
CREATE TABLE admin_mfa_credentials (
  admin_id      uuid PRIMARY KEY REFERENCES admin_users(id),
  secret_enc    bytea NOT NULL,
  confirmed_at  timestamptz,
  recovery_hash text[] NOT NULL DEFAULT '{}',
  created_at    timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE admin_audit_log (                                      -- RNF-021
  id           bigserial PRIMARY KEY,
  admin_id     uuid NOT NULL REFERENCES admin_users(id),
  action       text NOT NULL,
  target_type  text NOT NULL,
  target_id    text NOT NULL,
  reason       text NOT NULL,
  before_state jsonb,
  after_state  jsonb,
  ip           inet,
  created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON admin_audit_log (target_type, target_id, created_at DESC);

CREATE TRIGGER trg_audit_no_update BEFORE UPDATE ON admin_audit_log
  FOR EACH ROW EXECUTE FUNCTION ledger_is_append_only();
CREATE TRIGGER trg_audit_no_delete BEFORE DELETE ON admin_audit_log
  FOR EACH ROW EXECUTE FUNCTION ledger_is_append_only();

REVOKE UPDATE, DELETE ON admin_audit_log FROM paysi_app;
