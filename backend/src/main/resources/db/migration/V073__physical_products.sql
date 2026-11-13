ALTER TABLE products DROP CONSTRAINT products_segment_check;
ALTER TABLE products ADD CONSTRAINT products_segment_check
  CHECK (segment IN ('SAAS','DIGITAL','PHYSICAL'));

ALTER TABLE products ADD CONSTRAINT products_physical_one_time_check
  CHECK (segment <> 'PHYSICAL' OR charge_type = 'ONE_TIME');

-- Produto físico também pode oferecer boleto; produto digital continua limitado a Pix/cartão.
CREATE OR REPLACE FUNCTION offer_method_matches_segment() RETURNS trigger AS $$
DECLARE s text;
BEGIN
  SELECT segment INTO s FROM offers WHERE id = NEW.offer_id;
  IF NEW.method = 'BOLETO' AND s = 'DIGITAL' THEN
    RAISE EXCEPTION 'Boleto nao disponivel para produto digital';
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;
