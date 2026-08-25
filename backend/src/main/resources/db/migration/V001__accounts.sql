CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE accounts (
  id                  uuid PRIMARY KEY,
  email               citext NOT NULL,          -- unicidade: índice parcial abaixo
  password_hash       text NOT NULL,
  full_name           text NOT NULL,
  person_type         text NOT NULL CHECK (person_type IN ('PF','PJ')),
  tax_id              text NOT NULL,
  kyc_status          text NOT NULL DEFAULT 'PENDING'
                        CHECK (kyc_status IN ('PENDING','SUBMITTED','APPROVED','REJECTED')),
  provider_account_id text UNIQUE,
  payout_delay        text NOT NULL DEFAULT 'D32'
                        CHECK (payout_delay IN ('D32','D15','D7','D2')),
  -- [FIX-C4] a coluna `plan` foi REMOVIDA daqui.
  -- Fonte única do plano comercial: platform_subscriptions (V022).
  -- O plano aplicado a uma cobrança fica congelado em charges.plan (V008).
  risk_tier           int NOT NULL DEFAULT 0 CHECK (risk_tier BETWEEN 0 AND 3),
  status              text NOT NULL DEFAULT 'ACTIVE'
                        CHECK (status IN ('ACTIVE','LIMITED','SUSPENDED','CLOSED')),
  created_at          timestamptz NOT NULL DEFAULT now()
);

-- [FIX-D02] o índice de tax_id já liberava o documento após encerramento, mas
-- email era UNIQUE global: quem encerrava a conta nunca mais se recadastrava
-- com o mesmo e-mail. Os dois passam a ter a mesma regra.
CREATE UNIQUE INDEX uq_accounts_tax_id_open ON accounts (tax_id) WHERE status <> 'CLOSED';
CREATE UNIQUE INDEX uq_accounts_email_open  ON accounts (email)  WHERE status <> 'CLOSED';
