-- Evidência durável de alerta operacional (documento 3, §5.1; checklist #12).
-- Independente de outbox_events: aquela tabela é por conta, para entrega de
-- webhook ao vendedor; esta é da plataforma, para o canal de observabilidade
-- interno, e por isso não tem account_id.
CREATE TABLE ops_alerts (
  id           uuid PRIMARY KEY,
  alert_type   text NOT NULL,
  severity     text NOT NULL CHECK (severity IN ('CRITICAL','HIGH','MEDIUM','LOW')),
  payload      jsonb NOT NULL,
  created_at   timestamptz NOT NULL DEFAULT now(),
  delivered_at timestamptz
);
CREATE INDEX ON ops_alerts (alert_type, created_at);
CREATE INDEX ON ops_alerts (created_at) WHERE delivered_at IS NULL;
