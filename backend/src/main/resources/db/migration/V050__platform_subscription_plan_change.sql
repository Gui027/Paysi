-- BE-14.2: mudança de plano agendada (RF-114, sem efeito retroativo) e cartão
-- alternativo para a cobrança do Escala (RF-102: saldo disponível é a fonte
-- primária; cartão só entra se o saldo não cobrir).
ALTER TABLE platform_subscriptions
  ADD COLUMN pending_plan text CHECK (pending_plan IN ('TRANSACIONAL','ESCALA')),
  ADD COLUMN pending_price_cents bigint CHECK (pending_price_cents >= 0),
  ADD COLUMN pending_effective_at timestamptz,
  ADD COLUMN provider_token text,
  ADD CONSTRAINT pending_change_consistent CHECK (
    (pending_plan IS NULL AND pending_price_cents IS NULL AND pending_effective_at IS NULL) OR
    (pending_plan IS NOT NULL AND pending_price_cents IS NOT NULL AND pending_effective_at IS NOT NULL)
  );
