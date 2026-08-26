CREATE TABLE password_reset_tokens (
  id          uuid PRIMARY KEY,
  account_id  uuid NOT NULL REFERENCES accounts(id),
  token_hash  char(64) NOT NULL UNIQUE,
  expires_at  timestamptz NOT NULL,
  used_at     timestamptz,
  created_at  timestamptz NOT NULL DEFAULT now(),
  CHECK (expires_at > created_at),
  CHECK (used_at IS NULL OR used_at >= created_at)
);

CREATE INDEX ix_password_reset_tokens_account_created
  ON password_reset_tokens (account_id, created_at DESC);

CREATE INDEX ix_password_reset_tokens_open_expiration
  ON password_reset_tokens (expires_at)
  WHERE used_at IS NULL;
