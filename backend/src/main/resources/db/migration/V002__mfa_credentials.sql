CREATE TABLE mfa_credentials (                 -- RNF-022, RF-009 (usuários)
  account_id     uuid PRIMARY KEY REFERENCES accounts(id),
  secret_enc     bytea NOT NULL,
  confirmed_at   timestamptz,
  recovery_hash  text[] NOT NULL DEFAULT '{}',
  created_at     timestamptz NOT NULL DEFAULT now()
);
