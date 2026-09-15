CREATE FUNCTION outbox_product_fact() RETURNS trigger AS $$
BEGIN
  INSERT INTO outbox_events(id,account_id,event_type,payload,created_at)
  VALUES (gen_random_uuid(),NEW.seller_id,'PRODUCT.CHANGED',
          jsonb_build_object('productId',NEW.id,'status',NEW.status),now());
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_product_outbox AFTER INSERT OR UPDATE OF status ON products
  FOR EACH ROW EXECUTE FUNCTION outbox_product_fact();

CREATE FUNCTION outbox_affiliation_fact() RETURNS trigger AS $$
DECLARE v_seller uuid;
BEGIN
  SELECT seller_id INTO v_seller FROM products WHERE id=NEW.product_id;
  INSERT INTO outbox_events(id,account_id,event_type,payload,created_at)
  VALUES (gen_random_uuid(),v_seller,'AFFILIATION.CHANGED',
          jsonb_build_object('affiliationId',NEW.id,'productId',NEW.product_id,'status',NEW.status),now());
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_affiliation_outbox AFTER INSERT OR UPDATE OF status ON affiliations
  FOR EACH ROW EXECUTE FUNCTION outbox_affiliation_fact();

CREATE FUNCTION outbox_payout_fact() RETURNS trigger AS $$
BEGIN
  INSERT INTO outbox_events(id,account_id,event_type,payload,created_at)
  VALUES (gen_random_uuid(),NEW.account_id,'PAYOUT.CHANGED',
          jsonb_build_object('payoutId',NEW.id,'status',NEW.status),now());
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_payout_outbox AFTER INSERT OR UPDATE OF status ON payouts
  FOR EACH ROW EXECUTE FUNCTION outbox_payout_fact();

CREATE FUNCTION outbox_charge_fact() RETURNS trigger AS $$
DECLARE v_seller uuid;
BEGIN
  SELECT p.seller_id INTO v_seller
    FROM orders o JOIN offers f ON f.id=o.offer_id JOIN products p ON p.id=f.product_id
   WHERE o.id=NEW.order_id;
  INSERT INTO outbox_events(id,account_id,event_type,payload,created_at)
  VALUES (gen_random_uuid(),v_seller,'CHARGE.CHANGED',
          jsonb_build_object('chargeId',NEW.id,'orderId',NEW.order_id,'status',NEW.status),now());
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_charge_outbox AFTER INSERT OR UPDATE OF status ON charges
  FOR EACH ROW EXECUTE FUNCTION outbox_charge_fact();
