CREATE TABLE mfa_recovery_codes (
  account_id uuid NOT NULL REFERENCES mfa_credentials(account_id),
  code_hash  text NOT NULL,
  PRIMARY KEY (account_id, code_hash)
);

CREATE TABLE mfa_challenges (
  id          uuid PRIMARY KEY,
  account_id  uuid NOT NULL REFERENCES accounts(id),
  operation   text NOT NULL CHECK (operation IN ('BANK_ACCOUNT_CHANGE','PAYOUT')),
  expires_at  timestamptz NOT NULL,
  attempts    int NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 5),
  verified_at timestamptz,
  consumed_at timestamptz,
  created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_mfa_challenges_account ON mfa_challenges(account_id, expires_at DESC);
