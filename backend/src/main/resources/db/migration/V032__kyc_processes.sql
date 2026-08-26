CREATE TABLE kyc_processes (
  account_id          uuid PRIMARY KEY REFERENCES accounts(id),
  provider_process_id text NOT NULL UNIQUE,
  provider_url        text NOT NULL,
  expires_at          timestamptz NOT NULL,
  created_at          timestamptz NOT NULL DEFAULT now(),
  updated_at          timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE kyc_requirements (
  account_id   uuid NOT NULL REFERENCES accounts(id),
  code         text NOT NULL,
  label        text NOT NULL,
  status       text NOT NULL CHECK (status IN ('PENDING','SUBMITTED','APPROVED','REJECTED')),
  reason       text,
  estimated_at timestamptz,
  PRIMARY KEY (account_id, code)
);
