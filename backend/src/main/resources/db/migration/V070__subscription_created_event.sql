-- A criação da assinatura é um fato próprio; cobranças e mudanças de status continuam
-- sendo emitidas pelos triggers de ciclo já existentes.
CREATE FUNCTION outbox_subscription_created() RETURNS trigger AS $$
DECLARE
  v_seller  uuid;
  v_payload jsonb;
BEGIN
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
             'trialEndsAt', NEW.trial_ends_at,
             'nextChargeAt', NEW.next_charge_at,
             'buyer', jsonb_build_object('name', b.name, 'email', b.email::text))
      INTO v_seller, v_payload
      FROM orders o
      JOIN offers f ON f.id = o.offer_id
      JOIN products p ON p.id = f.product_id
      JOIN buyers b ON b.id = o.buyer_id
     WHERE o.id = NEW.order_id;
    IF v_seller IS NOT NULL THEN
      INSERT INTO outbox_events (id, account_id, event_type, payload, created_at)
      VALUES (gen_random_uuid(), v_seller, 'SUBSCRIPTION.CREATED', v_payload, now());
    END IF;
  EXCEPTION WHEN OTHERS THEN
    RAISE WARNING 'evento SUBSCRIPTION.CREATED não gerado para a assinatura %: %', NEW.id, SQLERRM;
  END;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_subscription_created_outbox AFTER INSERT ON subscriptions
  FOR EACH ROW EXECUTE FUNCTION outbox_subscription_created();
