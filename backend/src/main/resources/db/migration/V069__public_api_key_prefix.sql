-- Diferencia credenciais antigas (client credentials) das novas chaves Bearer autoidentificáveis.
-- As antigas continuam válidas pelo fluxo /oauth/token e aparecem corretamente no painel.
ALTER TABLE public_api_keys
  ADD COLUMN key_prefix text NOT NULL DEFAULT 'legacy';

ALTER TABLE public_api_keys
  ADD CONSTRAINT public_api_keys_prefix_check CHECK (key_prefix IN ('legacy', 'paysi_live'));
