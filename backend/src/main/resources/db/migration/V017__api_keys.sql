CREATE TABLE api_keys (
  id           uuid PRIMARY KEY,
  account_id   uuid NOT NULL REFERENCES accounts(id),
  name         text NOT NULL,
  -- [FIX-D2] HMAC-SHA256 com pepper no cofre, NÃO Argon2id.
  -- Argon2id é salgado por linha: não dá para procurar a chave a partir do
  -- segredo apresentado, e verificar linha a linha custa 50–200 ms por
  -- requisição, contra os 800 ms p95 inteiros do RNF-002.
  key_hash     text NOT NULL UNIQUE,          -- hex do HMAC
  prefix       text NOT NULL UNIQUE,          -- exibição e busca em O(1)
  scopes       text[] NOT NULL DEFAULT '{}',
  last_used_at timestamptz,
  revoked_at   timestamptz,
  created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON api_keys (account_id) WHERE revoked_at IS NULL;
