-- O checkout recorrente reutiliza o pedido durante retentativas. A unicidade torna
-- a criação da assinatura idempotente mesmo com duas requisições concorrentes.
CREATE UNIQUE INDEX uq_subscriptions_order ON subscriptions (order_id);
