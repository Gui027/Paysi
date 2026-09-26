-- Colaboradores convidados por um vendedor. A remoção do acesso apaga a linha.
CREATE TABLE collaborators (
  id          uuid PRIMARY KEY,
  account_id  uuid NOT NULL REFERENCES accounts(id),
  email       citext NOT NULL,
  status      text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','ACTIVE')),
  permissions text[] NOT NULL,
  invited_at  timestamptz NOT NULL DEFAULT now(),
  created_at  timestamptz NOT NULL DEFAULT now(),
  UNIQUE (account_id, email)
);
