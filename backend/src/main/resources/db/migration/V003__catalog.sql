CREATE TABLE products (
  id          uuid PRIMARY KEY,
  seller_id   uuid NOT NULL REFERENCES accounts(id),
  name        text NOT NULL,
  description text,
  segment     text NOT NULL CHECK (segment IN ('SAAS','DIGITAL')),   -- RF-092
  charge_type text NOT NULL CHECK (charge_type IN ('SUBSCRIPTION','ONE_TIME')),
  status      text NOT NULL DEFAULT 'DRAFT'
                CHECK (status IN ('DRAFT','ACTIVE','PAUSED','SUSPENDED')),
  archived_at timestamptz,
  created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON products (seller_id) WHERE archived_at IS NULL;

CREATE TABLE offers (
  id                     uuid PRIMARY KEY,
  product_id             uuid NOT NULL REFERENCES products(id),
  -- [FIX-A3] charge_type e segment são preenchidos POR GATILHO a partir de
  -- products no INSERT, e imutáveis daí em diante. A aplicação não os informa.
  charge_type            text NOT NULL,
  segment                text NOT NULL,
  slug                   text NOT NULL UNIQUE,
  amount_cents           bigint NOT NULL CHECK (amount_cents >= 2000),   -- RF-011
  cycle                  text CHECK (cycle IN ('MONTHLY','QUARTERLY','SEMIANNUAL','ANNUAL')),
  trial_days             int NOT NULL DEFAULT 0 CHECK (trial_days BETWEEN 0 AND 30),
  trial_requires_card    boolean NOT NULL DEFAULT true,                  -- RF-099
  guarantee_days         int NOT NULL DEFAULT 7 CHECK (guarantee_days >= 7),
  max_installments       int NOT NULL DEFAULT 1 CHECK (max_installments BETWEEN 1 AND 12),
  boleto_due_days        int NOT NULL DEFAULT 3 CHECK (boleto_due_days BETWEEN 1 AND 15),
  -- [FIX-D1] RF-098: antecedência de emissão do boleto do ciclo seguinte.
  boleto_cycle_lead_days int NOT NULL DEFAULT 5 CHECK (boleto_cycle_lead_days BETWEEN 3 AND 10),
  affiliates_enabled     boolean NOT NULL DEFAULT false,
  suggested_bps          int CHECK (suggested_bps BETWEEN 0 AND 5000),
  archived_at            timestamptz,
  created_at             timestamptz NOT NULL DEFAULT now(),

  -- [FIX-A3] agora compila: charge_type existe na própria tabela.
  CONSTRAINT cycle_matches_charge_type CHECK (
    (charge_type = 'SUBSCRIPTION' AND cycle IS NOT NULL) OR
    (charge_type = 'ONE_TIME'     AND cycle IS NULL)
  ),
  CONSTRAINT trial_card_rule CHECK (trial_requires_card OR segment = 'SAAS')
);

-- [FIX-A3] Gatilho de desnormalização. Roda BEFORE INSERT, portanto os CHECK
-- acima já enxergam os valores corretos.
CREATE FUNCTION offers_denormalize_from_product() RETURNS trigger AS $$
DECLARE p record;
BEGIN
  SELECT charge_type, segment INTO p FROM products WHERE id = NEW.product_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Produto % inexistente', NEW.product_id;
  END IF;
  NEW.charge_type := p.charge_type;
  NEW.segment     := p.segment;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_offers_denormalize BEFORE INSERT ON offers
  FOR EACH ROW EXECUTE FUNCTION offers_denormalize_from_product();

-- [FIX-A3] Imutabilidade das colunas desnormalizadas e do vínculo.
CREATE FUNCTION offers_lock_denormalized() RETURNS trigger AS $$
BEGIN
  IF NEW.product_id  <> OLD.product_id
  OR NEW.charge_type <> OLD.charge_type
  OR NEW.segment     <> OLD.segment THEN
    RAISE EXCEPTION 'product_id, charge_type e segment sao imutaveis na oferta';
  END IF;
  -- ciclo e prazo de garantia congelam quando já existe venda paga
  -- [FIX-D03] a condição olhava orders.status, que deixou de carregar estado
  -- de reembolso (V008). O fato "houve venda paga" mora na cobrança.
  IF (NEW.cycle IS DISTINCT FROM OLD.cycle OR NEW.guarantee_days <> OLD.guarantee_days)
     AND EXISTS (SELECT 1 FROM orders o JOIN charges c ON c.order_id = o.id
                 WHERE o.offer_id = OLD.id AND c.confirmed_at IS NOT NULL) THEN
    RAISE EXCEPTION 'cycle e guarantee_days imutaveis apos a primeira venda paga';
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_offers_lock BEFORE UPDATE ON offers
  FOR EACH ROW EXECUTE FUNCTION offers_lock_denormalized();

-- [FIX-A3] O produto não pode mudar de segmento/tipo depois de gerar oferta.
CREATE FUNCTION products_lock_after_offer() RETURNS trigger AS $$
BEGIN
  IF (NEW.segment <> OLD.segment OR NEW.charge_type <> OLD.charge_type)
     AND EXISTS (SELECT 1 FROM offers o WHERE o.product_id = OLD.id) THEN
    RAISE EXCEPTION 'segment e charge_type imutaveis apos existir oferta';
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_products_lock BEFORE UPDATE ON products
  FOR EACH ROW EXECUTE FUNCTION products_lock_after_offer();

CREATE TABLE offer_payment_methods (
  offer_id uuid NOT NULL REFERENCES offers(id),
  method   text NOT NULL CHECK (method IN ('PIX','CARD','BOLETO')),
  PRIMARY KEY (offer_id, method)
);

-- [FIX-D4] doc 1 §1.2: boleto só existe no segmento SAAS.
CREATE FUNCTION offer_method_matches_segment() RETURNS trigger AS $$
DECLARE s text;
BEGIN
  SELECT segment INTO s FROM offers WHERE id = NEW.offer_id;
  IF NEW.method = 'BOLETO' AND s <> 'SAAS' THEN
    RAISE EXCEPTION 'Boleto disponivel apenas no segmento SAAS';
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_offer_method_segment BEFORE INSERT OR UPDATE ON offer_payment_methods
  FOR EACH ROW EXECUTE FUNCTION offer_method_matches_segment();
