-- [FIX-B1] Padrão inbox — contrapartida do outbox do ADR-07.
-- O provedor também entrega "ao menos uma vez". Sem esta tabela, a segunda
-- entrega de payment.confirmed escreve uma segunda transação SALE que soma
-- zero, passa nas três verificações e credita o vendedor duas vezes.
CREATE TABLE provider_events (
  provider           text NOT NULL,
  provider_event_id  text NOT NULL,
  event_type         text NOT NULL,
  payload            jsonb NOT NULL,
  signature_valid    boolean NOT NULL,
  received_at        timestamptz NOT NULL DEFAULT now(),
  processed_at       timestamptz,
  status             text NOT NULL DEFAULT 'RECEIVED'
                       CHECK (status IN ('RECEIVED','PROCESSED','IGNORED','FAILED')),
  error              text,
  attempt_count      int NOT NULL DEFAULT 0,
  next_retry_at      timestamptz,
  PRIMARY KEY (provider, provider_event_id)
);
CREATE INDEX ON provider_events (status, next_retry_at) WHERE status IN ('RECEIVED','FAILED');
CREATE INDEX ON provider_events (received_at);
