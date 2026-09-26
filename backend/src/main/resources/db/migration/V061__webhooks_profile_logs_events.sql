-- Webhooks no formato do painel: nome, filtro por produto, exclusão lógica, corpo da requisição/resposta
-- guardado por tentativa (para a tela de logs) e os eventos que faltavam no catálogo.

ALTER TABLE webhook_endpoints
  ADD COLUMN name       text CHECK (name IS NULL OR length(name) <= 60),
  ADD COLUMN product_id uuid REFERENCES products(id),
  ADD COLUMN deleted_at timestamptz;

ALTER TABLE webhook_deliveries
  ADD COLUMN url           text,
  ADD COLUMN request_body  text,
  ADD COLUMN response_body text;

CREATE INDEX ix_webhook_deliveries_endpoint_created ON webhook_deliveries (endpoint_id, created_at DESC);

-- Eventos do ciclo da cobrança. Nunca derrubam a operação de origem: qualquer falha ao montar o evento
-- vira um aviso no log do banco.
--   PIX.GENERATED / BOLETO.GENERATED  cobrança criada aguardando pagamento
--   PAYMENT.REFUSED                   cobrança recusada
--   CART.ABANDONED                    primeira cobrança expirou sem pagamento
--   SUBSCRIPTION.RENEWED              ciclo seguinte de uma assinatura foi pago
CREATE FUNCTION outbox_charge_lifecycle() RETURNS trigger AS $$
DECLARE
  v_type    text;
  v_seller  uuid;
  v_payload jsonb;
BEGIN
  BEGIN
    IF TG_OP = 'INSERT' THEN
      IF NEW.status = 'PENDING' THEN
        SELECT CASE o.method WHEN 'PIX' THEN 'PIX.GENERATED' WHEN 'BOLETO' THEN 'BOLETO.GENERATED' END
          INTO v_type FROM orders o WHERE o.id = NEW.order_id;
      ELSIF NEW.status = 'PAID' AND NEW.cycle_number > 1 AND NEW.subscription_id IS NOT NULL THEN
        v_type := 'SUBSCRIPTION.RENEWED';
      END IF;
    ELSIF NEW.status IS DISTINCT FROM OLD.status THEN
      IF NEW.status = 'FAILED' THEN
        v_type := 'PAYMENT.REFUSED';
      ELSIF NEW.status = 'EXPIRED' AND NEW.cycle_number = 1 THEN
        v_type := 'CART.ABANDONED';
      ELSIF NEW.status = 'PAID' AND NEW.cycle_number > 1 AND NEW.subscription_id IS NOT NULL THEN
        v_type := 'SUBSCRIPTION.RENEWED';
      END IF;
    END IF;
    IF v_type IS NULL THEN RETURN NEW; END IF;

    SELECT p.seller_id,
           jsonb_build_object(
             'chargeId', NEW.id,
             'orderId', NEW.order_id,
             'reference', o.external_ref,
             'offerId', f.id,
             'offerName', f.name,
             'productId', p.id,
             'productName', p.name,
             'amountCents', NEW.amount_cents,
             'currency', 'BRL',
             'method', o.method,
             'installments', o.installments,
             'status', NEW.status,
             'subscriptionId', NEW.subscription_id,
             'cycleNumber', NEW.cycle_number,
             'buyer', jsonb_build_object('name', b.name, 'email', b.email::text))
      INTO v_seller, v_payload
      FROM orders o
      JOIN offers f ON f.id = o.offer_id
      JOIN products p ON p.id = f.product_id
      JOIN buyers b ON b.id = o.buyer_id
     WHERE o.id = NEW.order_id;
    IF v_seller IS NOT NULL THEN
      INSERT INTO outbox_events (id, account_id, event_type, payload, created_at)
      VALUES (gen_random_uuid(), v_seller, v_type, v_payload, now());
    END IF;
  EXCEPTION WHEN OTHERS THEN
    RAISE WARNING 'evento do ciclo da cobrança não gerado para %: %', NEW.id, SQLERRM;
  END;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_charge_lifecycle_outbox AFTER INSERT OR UPDATE OF status ON charges
  FOR EACH ROW EXECUTE FUNCTION outbox_charge_lifecycle();
