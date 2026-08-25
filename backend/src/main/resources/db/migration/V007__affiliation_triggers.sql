CREATE FUNCTION lock_approved_affiliation() RETURNS trigger AS $$   -- RF-046
BEGIN
  IF OLD.status = 'APPROVED' AND
     (NEW.commission_bps <> OLD.commission_bps OR NEW.recurring <> OLD.recurring)
  THEN RAISE EXCEPTION 'Comissao imutavel apos aprovacao';
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_lock_affiliation BEFORE UPDATE ON affiliations
  FOR EACH ROW EXECUTE FUNCTION lock_approved_affiliation();

CREATE FUNCTION reject_self_affiliation() RETURNS trigger AS $$     -- RF-049
BEGIN
  IF EXISTS (SELECT 1 FROM products p
             WHERE p.id = NEW.product_id AND p.seller_id = NEW.affiliate_id)
  THEN RAISE EXCEPTION 'Autoafiliacao vedada';
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_no_self_affiliation BEFORE INSERT OR UPDATE ON affiliations
  FOR EACH ROW EXECUTE FUNCTION reject_self_affiliation();
