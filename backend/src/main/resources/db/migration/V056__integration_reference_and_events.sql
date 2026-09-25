-- Integração com o sistema do vendedor:
--  * external_ref: identificador do cliente no sistema do vendedor, recebido no link do checkout
--    (?ref=...) e devolvido nos webhooks;
--  * return_url: para onde o comprador volta depois do pagamento;
--  * eventos PAYMENT.APPROVED e SUBSCRIPTION.PAST_DUE / SUBSCRIPTION.CANCELED no outbox.
-- Os gatilhos de evento nunca derrubam a operação de origem: qualquer falha ao montar o evento
-- vira um aviso no log do banco (o pagamento e a assinatura seguem normalmente).

ALTER TABLE orders
  ADD COLUMN external_ref text CHECK (external_ref IS NULL OR length(external_ref) <= 128);

ALTER TABLE offers
  ADD COLUMN return_url text CHECK (return_url IS NULL OR length(return_url) <= 500);

CREATE FUNCTION outbox_payment_approved() RETURNS trigger AS $$
DECLARE
  v_seller  uuid;
  v_payload jsonb;
BEGIN
  IF NEW.status <> 'PAID' THEN RETURN NEW; END IF;
  IF TG_OP = 'UPDATE' AND OLD.status = 'PAID' THEN RETURN NEW; END IF;
  BEGIN
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
             'subscriptionId', NEW.subscription_id,
             'cycleNumber', NEW.cycle_number,
             'paidAt', COALESCE(NEW.confirmed_at, now()),
             'buyer', jsonb_build_object('name', b.name, 'email', b.email::text))
      INTO v_seller, v_payload
      FROM orders o
      JOIN offers f ON f.id = o.offer_id
      JOIN products p ON p.id = f.product_id
      JOIN buyers b ON b.id = o.buyer_id
     WHERE o.id = NEW.order_id;
    IF v_seller IS NOT NULL THEN
      INSERT INTO outbox_events (id, account_id, event_type, payload, created_at)
      VALUES (gen_random_uuid(), v_seller, 'PAYMENT.APPROVED', v_payload, now());
    END IF;
  EXCEPTION WHEN OTHERS THEN
    RAISE WARNING 'evento PAYMENT.APPROVED não gerado para a cobrança %: %', NEW.id, SQLERRM;
  END;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_payment_approved_outbox AFTER INSERT OR UPDATE OF status ON charges
  FOR EACH ROW EXECUTE FUNCTION outbox_payment_approved();

CREATE FUNCTION outbox_subscription_status() RETURNS trigger AS $$
DECLARE
  v_type    text;
  v_seller  uuid;
  v_payload jsonb;
BEGIN
  IF NEW.status = OLD.status THEN RETURN NEW; END IF;
  IF NEW.status = 'PAST_DUE' THEN
    v_type := 'SUBSCRIPTION.PAST_DUE';
  ELSIF NEW.status = 'CANCELED' THEN
    v_type := 'SUBSCRIPTION.CANCELED';
  ELSE
    RETURN NEW;
  END IF;
  BEGIN
    SELECT p.seller_id,
           jsonb_build_object(
             'subscriptionId', NEW.id,
             'orderId', NEW.order_id,
             'reference', o.external_ref,
             'offerId', f.id,
             'offerName', f.name,
             'productId', p.id,
             'productName', p.name,
             'status', NEW.status,
             'cycleNumber', NEW.cycle_number,
             'canceledAt', NEW.canceled_at,
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
    RAISE WARNING 'evento % não gerado para a assinatura %: %', v_type, NEW.id, SQLERRM;
  END;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_subscription_status_outbox AFTER UPDATE OF status ON subscriptions
  FOR EACH ROW EXECUTE FUNCTION outbox_subscription_status();
