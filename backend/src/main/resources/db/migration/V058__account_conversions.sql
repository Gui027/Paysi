-- Auditoria da mudança de conta pessoa física -> pessoa jurídica (irreversível).
CREATE TABLE account_conversions (
  id                   uuid PRIMARY KEY,
  account_id           uuid NOT NULL REFERENCES accounts(id),
  previous_person_type text NOT NULL,
  previous_tax_id      text NOT NULL,
  new_tax_id           text NOT NULL,
  legal_name           text NOT NULL,
  created_at           timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON account_conversions (account_id);
