-- Chaves da API pública. O segredo só existe em claro no momento da criação: guardamos o hash SHA-256
-- (o segredo tem 256 bits aleatórios) e os quatro últimos caracteres para o vendedor reconhecer a chave.
CREATE TABLE api_keys (
  id           uuid PRIMARY KEY,
  account_id   uuid NOT NULL REFERENCES accounts(id),
  name         text NOT NULL,
  client_id    uuid NOT NULL UNIQUE,
  secret_hash  text NOT NULL,
  secret_last4 text NOT NULL,
  scopes       text[] NOT NULL,
  created_at   timestamptz NOT NULL DEFAULT now(),
  last_used_at timestamptz
);
CREATE INDEX ON api_keys (account_id, created_at DESC);

-- Tokens de acesso de curta duração emitidos a partir de client_id + client_secret.
CREATE TABLE api_access_tokens (
  token_hash text PRIMARY KEY,
  api_key_id uuid NOT NULL REFERENCES api_keys(id) ON DELETE CASCADE,
  expires_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON api_access_tokens (expires_at);
