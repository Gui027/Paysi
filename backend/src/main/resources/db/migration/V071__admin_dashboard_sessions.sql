-- Sessões exclusivas da dashboard administrativa de leitura. Os tokens completos
-- nunca são persistidos; somente SHA-256, como nas sessões das contas.
CREATE TABLE admin_dashboard_sessions (
  token_hash   char(64) PRIMARY KEY,
  username     text NOT NULL,
  last_seen_at timestamptz NOT NULL,
  expires_at   timestamptz NOT NULL,
  created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_admin_dashboard_sessions_expiry ON admin_dashboard_sessions (expires_at);
