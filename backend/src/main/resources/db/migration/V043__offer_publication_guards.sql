-- BE-03.3: status is a state machine even for writes that bypass the API.
CREATE FUNCTION offers_guard_status_transition() RETURNS trigger AS $$
BEGIN
  IF OLD.status = 'ARCHIVED' AND NEW.status <> OLD.status THEN
    RAISE EXCEPTION 'oferta arquivada nao pode mudar de estado';
  END IF;
  IF OLD.status = 'PUBLISHED' AND NEW.status = 'DRAFT' THEN
    RAISE EXCEPTION 'oferta publicada nao pode voltar para rascunho';
  END IF;
  IF NEW.status = 'ARCHIVED' AND NEW.archived_at IS NULL THEN
    RAISE EXCEPTION 'oferta arquivada exige archived_at';
  END IF;
  IF NEW.status <> 'ARCHIVED' AND NEW.archived_at IS NOT NULL THEN
    RAISE EXCEPTION 'archived_at exige estado ARCHIVED';
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_offers_status_transition BEFORE UPDATE ON offers
  FOR EACH ROW EXECUTE FUNCTION offers_guard_status_transition();

