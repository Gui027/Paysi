-- =====================================================================
-- Paysi — Esquema de banco, versão 2.1
-- Conjunto completo de migrações Flyway, em ordem de aplicação.
--
-- Cada bloco delimitado por "=== Vnnn__nome.sql ===" é um arquivo
-- separado em backend/src/main/resources/db/migration/.
-- Este arquivo único existe para revisão; divida-o antes de rodar.
--
-- Base: documento 2, §3. Correções da revisão v2.1 marcadas [FIX-nn];
-- correções da revisão v3.0 marcadas [FIX-Dnn], todas verificadas contra um
-- PostgreSQL 16.15 real pela suíte tests.sql.
-- =====================================================================


-- =====================================================================
-- === V000__roles.sql ===
-- =====================================================================

-- [FIX-D01] V011 e V023 faziam REVOKE ... FROM paysi_app sobre um papel que
-- nenhuma migração criava: a migração aborta em ambiente limpo (CI, máquina
-- nova, flyway:clean flyway:migrate). Criar o papel é pré-requisito, não
-- configuração de infraestrutura.
--
-- E vale o alerta que faltava: o REVOKE só protege se a aplicação NÃO for a
-- dona das tabelas. O dono ignora GRANT/REVOKE. O .env do documento 5 conecta
-- como `paysi`, que é o dono — logo o REVOKE não protegeria nada. Quem migra
-- é `paysi`; quem atende requisição é `paysi_app`.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'paysi_app') THEN
    CREATE ROLE paysi_app LOGIN;
  END IF;
END $$;


-- =====================================================================
-- === V001__accounts.sql ===
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE accounts (
  id                  uuid PRIMARY KEY,
  email               citext NOT NULL,          -- unicidade: índice parcial abaixo
  password_hash       text NOT NULL,
  full_name           text NOT NULL,
  person_type         text NOT NULL CHECK (person_type IN ('PF','PJ')),
  tax_id              text NOT NULL,
  kyc_status          text NOT NULL DEFAULT 'PENDING'
                        CHECK (kyc_status IN ('PENDING','SUBMITTED','APPROVED','REJECTED')),
  provider_account_id text UNIQUE,
  payout_delay        text NOT NULL DEFAULT 'D32'
                        CHECK (payout_delay IN ('D32','D15','D7','D2')),
  -- [FIX-C4] a coluna `plan` foi REMOVIDA daqui.
  -- Fonte única do plano comercial: platform_subscriptions (V022).
  -- O plano aplicado a uma cobrança fica congelado em charges.plan (V008).
  risk_tier           int NOT NULL DEFAULT 0 CHECK (risk_tier BETWEEN 0 AND 3),
  status              text NOT NULL DEFAULT 'ACTIVE'
                        CHECK (status IN ('ACTIVE','LIMITED','SUSPENDED','CLOSED')),
  created_at          timestamptz NOT NULL DEFAULT now()
);

-- [FIX-D02] o índice de tax_id já liberava o documento após encerramento, mas
-- email era UNIQUE global: quem encerrava a conta nunca mais se recadastrava
-- com o mesmo e-mail. Os dois passam a ter a mesma regra.
CREATE UNIQUE INDEX uq_accounts_tax_id_open ON accounts (tax_id) WHERE status <> 'CLOSED';
CREATE UNIQUE INDEX uq_accounts_email_open  ON accounts (email)  WHERE status <> 'CLOSED';


-- =====================================================================
-- === V002__mfa_credentials.sql ===
-- =====================================================================

CREATE TABLE mfa_credentials (                 -- RNF-022, RF-009 (usuários)
  account_id     uuid PRIMARY KEY REFERENCES accounts(id),
  secret_enc     bytea NOT NULL,
  confirmed_at   timestamptz,
  recovery_hash  text[] NOT NULL DEFAULT '{}',
  created_at     timestamptz NOT NULL DEFAULT now()
);


-- =====================================================================
-- === V003__catalog.sql ===
-- =====================================================================

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


-- =====================================================================
-- === V004__coupons.sql ===
-- =====================================================================

CREATE TABLE coupons (                                              -- RF-027
  id              uuid PRIMARY KEY,
  seller_id       uuid NOT NULL REFERENCES accounts(id),
  code            citext NOT NULL,
  kind            text NOT NULL CHECK (kind IN ('PERCENT','FIXED')),
  value           int NOT NULL CHECK (value > 0),   -- bps se PERCENT, centavos se FIXED
  max_redemptions int CHECK (max_redemptions IS NULL OR max_redemptions > 0),
  max_per_buyer   int NOT NULL DEFAULT 1 CHECK (max_per_buyer >= 1),
  redeemed_count  int NOT NULL DEFAULT 0 CHECK (redeemed_count >= 0),
  expires_at      timestamptz,
  archived_at     timestamptz,
  created_at      timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT percent_range CHECK (kind <> 'PERCENT' OR value BETWEEN 1 AND 10000),
  -- [FIX-B5] rede de segurança: mesmo que o UPDATE condicional falhe,
  -- o banco não deixa o contador passar do teto.
  CONSTRAINT redemption_cap CHECK (max_redemptions IS NULL OR redeemed_count <= max_redemptions)
);
CREATE UNIQUE INDEX uq_coupons_seller_code ON coupons (seller_id, code) WHERE archived_at IS NULL;

CREATE TABLE coupon_offers (
  coupon_id uuid NOT NULL REFERENCES coupons(id),
  offer_id  uuid NOT NULL REFERENCES offers(id),
  PRIMARY KEY (coupon_id, offer_id)
);

-- [FIX-B5] Trilha de resgate: sustenta max_per_buyer, auditoria e estorno.
CREATE TABLE coupon_redemptions (
  coupon_id   uuid NOT NULL REFERENCES coupons(id),
  order_id    uuid NOT NULL,          -- FK adicionada em V008 (orders vem depois)
  buyer_id    uuid NOT NULL,
  amount_cents bigint NOT NULL CHECK (amount_cents > 0),
  redeemed_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (coupon_id, order_id)
);
CREATE INDEX ON coupon_redemptions (coupon_id, buyer_id);


-- =====================================================================
-- === V005__buyers.sql ===
-- =====================================================================

CREATE TABLE buyers (
  id             uuid PRIMARY KEY,
  email          citext NOT NULL,
  tax_id         text NOT NULL,
  person_type    text NOT NULL CHECK (person_type IN ('PF','PJ')),
  name           text NOT NULL,
  legal_name     text,                                              -- RF-093
  municipal_reg  text,
  address        jsonb,
  anonymized_at  timestamptz,                                       -- RNF-026
  created_at     timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_buyers_taxid_email ON buyers (tax_id, email) WHERE anonymized_at IS NULL;
CREATE INDEX ON buyers (email);
CREATE INDEX ON buyers (tax_id);       -- monitoramento AM-03


-- =====================================================================
-- === V006__affiliations.sql ===
-- =====================================================================

CREATE TABLE affiliations (
  id              uuid PRIMARY KEY,
  product_id      uuid NOT NULL REFERENCES products(id),
  affiliate_id    uuid NOT NULL REFERENCES accounts(id),
  commission_bps  int NOT NULL CHECK (commission_bps BETWEEN 0 AND 5000),
  recurring       boolean NOT NULL,
  status          text NOT NULL DEFAULT 'REQUESTED'
                    CHECK (status IN ('REQUESTED','APPROVED','REJECTED','ENDED')),
  ended_reason    text CHECK (ended_reason IN ('BY_SELLER','BY_AFFILIATE','FRAUD')),
  approved_at     timestamptz,
  ended_at        timestamptz,
  created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_affiliation_active ON affiliations (product_id, affiliate_id)
  WHERE status IN ('REQUESTED','APPROVED');

CREATE TABLE affiliate_clicks (
  id              uuid PRIMARY KEY,
  affiliation_id  uuid NOT NULL REFERENCES affiliations(id),
  product_id      uuid NOT NULL REFERENCES products(id),
  visitor_key     text NOT NULL,
  ip              inet,
  created_at      timestamptz NOT NULL DEFAULT now(),
  expires_at      timestamptz NOT NULL                              -- created_at + 60 dias
);
CREATE INDEX ON affiliate_clicks (visitor_key, product_id, created_at DESC);
CREATE INDEX ON affiliate_clicks (expires_at);


-- =====================================================================
-- === V007__affiliation_triggers.sql ===
-- =====================================================================

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


-- =====================================================================
-- === V008__orders_charges_subscriptions.sql ===
-- =====================================================================

CREATE TABLE orders (
  id              uuid PRIMARY KEY,
  offer_id        uuid NOT NULL REFERENCES offers(id),
  buyer_id        uuid NOT NULL REFERENCES buyers(id),
  affiliation_id  uuid REFERENCES affiliations(id),
  buyer_snapshot  jsonb NOT NULL,
  gross_cents     bigint NOT NULL CHECK (gross_cents >= 2000),
  discount_cents  bigint NOT NULL DEFAULT 0 CHECK (discount_cents >= 0),
  coupon_id       uuid REFERENCES coupons(id),
  paid_cents      bigint NOT NULL,
  method          text NOT NULL CHECK (method IN ('PIX','CARD','BOLETO')),
  installments    int NOT NULL DEFAULT 1 CHECK (installments BETWEEN 1 AND 12),
  -- [FIX-D04] o pedido carrega o CICLO DE VIDA DA COMPRA; o dinheiro (e portanto
  -- reembolso, contestação e plano aplicado) vive na COBRANÇA.
  --
  -- Com o acumulador aqui, uma assinatura de doze ciclos tem doze cobranças
  -- apontando para UM pedido cujo paid_cents é o do primeiro ciclo. Reembolsar
  -- dois ciclos de R$ 100 acumularia 20000 contra um teto de 10000 e a
  -- transação abortaria — reembolso legítimo recusado pelo banco. Reproduzido
  -- em teste (H1) antes da correção.
  --
  -- O estado consolidado do pedido passa a ser a visão v_order_status (V028).
  status          text NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING','PAID','FAILED','EXPIRED')),
  idempotency_key text NOT NULL,
  request_hash    text NOT NULL,       -- mesma chave + corpo diferente => 409
  confirmed_at    timestamptz,         -- [FIX-B3] marco do fato gerador contábil
  created_at      timestamptz NOT NULL DEFAULT now(),

  CHECK (paid_cents = gross_cents - discount_cents),
  -- [FIX-A2] piso técnico da cobrança (R$ 5,00), separado do piso comercial
  -- da oferta (R$ 20,00, RF-011). Cupom pode descer até aqui e não abaixo.
  CHECK (paid_cents >= 500)
);
CREATE UNIQUE INDEX uq_orders_idem ON orders (offer_id, idempotency_key);
CREATE INDEX ON orders (buyer_id);
CREATE INDEX ON orders (offer_id, created_at DESC);

ALTER TABLE coupon_redemptions
  ADD CONSTRAINT fk_coupon_redemption_order FOREIGN KEY (order_id) REFERENCES orders(id),
  ADD CONSTRAINT fk_coupon_redemption_buyer FOREIGN KEY (buyer_id) REFERENCES buyers(id);

CREATE TABLE subscriptions (
  id              uuid PRIMARY KEY,
  order_id        uuid NOT NULL REFERENCES orders(id),
  offer_id        uuid NOT NULL REFERENCES offers(id),
  affiliation_id  uuid REFERENCES affiliations(id),
  status          text NOT NULL DEFAULT 'TRIAL'
                    CHECK (status IN ('TRIAL','ACTIVE','PAST_DUE','CANCELED')),
  cycle_number    int NOT NULL DEFAULT 0,
  trial_ends_at   timestamptz,
  next_charge_at  timestamptz,
  canceled_at     timestamptz,
  provider_token  text,
  created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON subscriptions (next_charge_at) WHERE status IN ('ACTIVE','PAST_DUE');

CREATE TABLE charges (
  id                      uuid PRIMARY KEY,
  order_id                uuid NOT NULL REFERENCES orders(id),
  subscription_id         uuid REFERENCES subscriptions(id),
  cycle_number            int NOT NULL DEFAULT 1,
  amount_cents            bigint NOT NULL CHECK (amount_cents > 0),

  -- [FIX-C4] memória de cálculo congelada: reconciliação deixa de depender
  -- da tabela de preços vigente hoje.
  plan                    text NOT NULL CHECK (plan IN ('TRANSACIONAL','ESCALA')),
  platform_fee_bps        int NOT NULL CHECK (platform_fee_bps >= 0),
  platform_fee_fixed_cents bigint NOT NULL CHECK (platform_fee_fixed_cents >= 0),
  platform_fee_cents      bigint NOT NULL CHECK (platform_fee_cents >= 0),  -- taxa cobrada do vendedor
  affiliate_fee_cents     bigint NOT NULL DEFAULT 0 CHECK (affiliate_fee_cents >= 0),
  seller_amount_cents     bigint NOT NULL CHECK (seller_amount_cents >= 0),
  provider_fee_cents      bigint,                                  -- REAL, informado pelo provedor

  status                  text NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN ('PENDING','PAID','FAILED','EXPIRED',
                                              'PARTIALLY_REFUNDED','REFUNDED','CHARGEBACK')),
  refunded_cents          bigint NOT NULL DEFAULT 0 CHECK (refunded_cents >= 0),
  provider_charge_id      text UNIQUE,
  three_ds_result         text CHECK (three_ds_result IN
                            ('NOT_APPLICABLE','AUTHENTICATED','ATTEMPTED','FAILED')),  -- RF-100
  attempt_count           int NOT NULL DEFAULT 1,
  next_retry_at           timestamptz,
  paid_at                 timestamptz,
  confirmed_at            timestamptz,                             -- [FIX-B3]
  created_at              timestamptz NOT NULL DEFAULT now(),

  CHECK (refunded_cents <= amount_cents),
  -- invariante do doc 1 §3.4, verificada também no banco
  CHECK (seller_amount_cents + affiliate_fee_cents + platform_fee_cents = amount_cents)
);
CREATE INDEX ON charges (order_id);
CREATE INDEX ON charges (next_retry_at) WHERE status = 'FAILED' AND next_retry_at IS NOT NULL;
-- [FIX-B9] UNIQUE, não índice comum. A cobrança de assinatura é o único
-- caminho que movimenta dinheiro SEM chave de idempotência vinda do cliente
-- — não existe navegador para gerar uma. (subscription_id, cycle_number) é a
-- chave natural, e sem unicidade uma retentativa do processo de 15 minutos
-- cobra o mesmo ciclo duas vezes. ShedLock estreita a janela; não a fecha.
CREATE UNIQUE INDEX uq_charges_subscription_cycle
  ON charges (subscription_id, cycle_number) WHERE subscription_id IS NOT NULL;


-- =====================================================================
-- === V009__receivables.sql ===
-- =====================================================================

CREATE TABLE receivables (                                          -- RF-041
  id                      uuid PRIMARY KEY,
  charge_id               uuid NOT NULL REFERENCES charges(id),
  installment_number      int NOT NULL CHECK (installment_number >= 1),
  amount_cents            bigint NOT NULL CHECK (amount_cents > 0),
  -- [FIX-B6] parte do vendedor e do afiliado nesta parcela, já rateadas pelo
  -- método do maior resto. Gravadas, não recalculadas: o rateio é feito uma vez.
  seller_amount_cents     bigint NOT NULL CHECK (seller_amount_cents >= 0),
  affiliate_amount_cents  bigint NOT NULL DEFAULT 0 CHECK (affiliate_amount_cents >= 0),
  expected_at             timestamptz NOT NULL,
  settled_at              timestamptz,
  provider_receivable_id  text,
  UNIQUE (charge_id, installment_number),
  -- [FIX-D05] o rateio por parcela era gravado sem nenhuma amarração: uma
  -- parcela aceitava parte do vendedor maior que a própria parcela (H2).
  CHECK (seller_amount_cents + affiliate_amount_cents <= amount_cents)
);
CREATE INDEX ON receivables (expected_at) WHERE settled_at IS NULL;


-- =====================================================================
-- === V010__ledger.sql ===
-- =====================================================================

-- [FIX-A1] Contas de sistema passam a declarar o próprio saldo normal.
-- Sem isso a verificação de não negatividade acusa SYS_CLEARING todo dia.
CREATE TABLE ledger_accounts (
  id             uuid PRIMARY KEY,
  code           text NOT NULL UNIQUE,
  name           text NOT NULL,
  normal_balance text NOT NULL CHECK (normal_balance IN ('DEBIT','CREDIT')),
  created_at     timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE ledger_transactions (
  id             uuid PRIMARY KEY,
  type           text NOT NULL CHECK (type IN
                   ('SALE','GUARANTEE_RELEASE','RELEASE','RESERVE_RELEASE',
                    'REFUND','CHARGEBACK','CHARGEBACK_REVERSAL',
                    'PAYOUT','PAYOUT_REVERSAL','PLATFORM_FEE',
                    'DEBT_WRITEOFF','ADJUSTMENT','ANTICIPATION')),
  -- [FIX-B1] referência estruturada + chave natural única: a mesma notificação
  -- do provedor entregue duas vezes não gera duas transações de venda.
  reference_type text NOT NULL CHECK (reference_type IN
                   ('CHARGE','REFUND','DISPUTE','PAYOUT','RECEIVABLE',
                    'PLATFORM_SUB','VERIFICATION','DEBT_WRITEOFF','ADJUSTMENT')),
  reference_id   text NOT NULL,
  description    text NOT NULL,
  created_at     timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_ledger_tx_natural
  ON ledger_transactions (type, reference_type, reference_id);

CREATE TABLE ledger_entries (
  id             bigserial PRIMARY KEY,
  transaction_id uuid NOT NULL REFERENCES ledger_transactions(id),
  account_id     uuid NOT NULL,   -- accounts.id  OU  ledger_accounts.id (ver gatilho V011)
  bucket         text NOT NULL CHECK (bucket IN
                   ('GUARANTEE','PENDING','RESERVE','AVAILABLE','DEBT','SYSTEM')),
  direction      text NOT NULL CHECK (direction IN ('DEBIT','CREDIT')),
  amount_cents   bigint NOT NULL CHECK (amount_cents > 0),
  origin         text NOT NULL CHECK (origin IN ('SALE','COMMISSION','FEE','DEBT','OTHER')),
  release_at     timestamptz,
  created_at     timestamptz NOT NULL DEFAULT now(),
  -- [FIX-D06] release_at agenda a SAÍDA de um valor que entrou. Em um débito
  -- ele agendaria a liberação de dinheiro que já saiu do bucket. Era aceito.
  CONSTRAINT release_only_on_credit CHECK (release_at IS NULL OR direction = 'CREDIT'),
  CONSTRAINT release_never_on_system CHECK (release_at IS NULL OR bucket <> 'SYSTEM')
);
CREATE INDEX ON ledger_entries (account_id, bucket) INCLUDE (direction, amount_cents);
CREATE INDEX ON ledger_entries (transaction_id);

-- [FIX-G1] Projeção MUTÁVEL de agendamento.
-- ledger_entries é append-only, logo não há onde marcar "já liberado".
-- Sem esta tabela, o processo horário de saída da garantia move o MESMO
-- dinheiro a cada hora, indefinidamente. É derivada e reconstruível.
CREATE TABLE ledger_release_schedule (
  entry_id               bigint PRIMARY KEY REFERENCES ledger_entries(id),
  account_id             uuid NOT NULL,
  bucket                 text NOT NULL,
  amount_cents           bigint NOT NULL CHECK (amount_cents > 0),
  release_at             timestamptz NOT NULL,
  released_at            timestamptz,
  release_transaction_id uuid REFERENCES ledger_transactions(id)
);
CREATE INDEX ON ledger_release_schedule (release_at) WHERE released_at IS NULL;
CREATE INDEX ON ledger_release_schedule (account_id, bucket) WHERE released_at IS NULL;


-- =====================================================================
-- === V011__ledger_immutability_triggers.sql ===
-- =====================================================================

CREATE FUNCTION ledger_is_append_only() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'ledger_entries e append-only. Corrija por lancamento inverso.';
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_no_update BEFORE UPDATE ON ledger_entries
  FOR EACH ROW EXECUTE FUNCTION ledger_is_append_only();
CREATE TRIGGER trg_ledger_no_delete BEFORE DELETE ON ledger_entries
  FOR EACH ROW EXECUTE FUNCTION ledger_is_append_only();

CREATE TRIGGER trg_ledger_tx_no_update BEFORE UPDATE ON ledger_transactions
  FOR EACH ROW EXECUTE FUNCTION ledger_is_append_only();
CREATE TRIGGER trg_ledger_tx_no_delete BEFORE DELETE ON ledger_transactions
  FOR EACH ROW EXECUTE FUNCTION ledger_is_append_only();

REVOKE UPDATE, DELETE ON ledger_entries      FROM paysi_app;
REVOKE UPDATE, DELETE ON ledger_transactions FROM paysi_app;

-- [FIX-A1] Integridade referencial que o modelo original não tinha:
-- account_id apontava para "conta de usuário ou de sistema", sem FK nenhuma.
CREATE FUNCTION ledger_entry_account_valid() RETURNS trigger AS $$
BEGIN
  IF NEW.bucket = 'SYSTEM' THEN
    IF NOT EXISTS (SELECT 1 FROM ledger_accounts WHERE id = NEW.account_id) THEN
      RAISE EXCEPTION 'bucket SYSTEM exige conta de sistema existente: %', NEW.account_id;
    END IF;
  ELSE
    IF NOT EXISTS (SELECT 1 FROM accounts WHERE id = NEW.account_id) THEN
      RAISE EXCEPTION 'bucket de usuario exige conta existente: %', NEW.account_id;
    END IF;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_account_valid BEFORE INSERT ON ledger_entries
  FOR EACH ROW EXECUTE FUNCTION ledger_entry_account_valid();

-- [FIX-D07] A projeção de agendamento passa a ser POPULADA PELO BANCO.
-- Antes era responsabilidade da aplicação lembrar de inserir a linha (item 20
-- da lista de revisão do doc 5). Esquecer significa dinheiro que nunca sai da
-- garantia — silenciosamente, para sempre. Um item de lista de revisão que o
-- banco pode garantir não deveria ser item de lista de revisão.
CREATE FUNCTION ledger_schedule_release() RETURNS trigger AS $$
BEGIN
  IF NEW.release_at IS NOT NULL THEN
    INSERT INTO ledger_release_schedule (entry_id, account_id, bucket, amount_cents, release_at)
    VALUES (NEW.id, NEW.account_id, NEW.bucket, NEW.amount_cents, NEW.release_at);
  END IF;
  RETURN NULL;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_schedule AFTER INSERT ON ledger_entries
  FOR EACH ROW EXECUTE FUNCTION ledger_schedule_release();

-- [FIX-D08] Não negatividade deixa de ser achado do dia seguinte e passa a ser
-- impossível. Gatilho de restrição DEFERIDO: roda no COMMIT, portanto a ordem
-- dos lançamentos dentro da transação não importa.
--
-- RNF-032 dizia "verificado por consulta diária". Consulta diária descobre que
-- o saldo ficou negativo ontem; o saque já saiu. As verificações 2 e 3 do §3.6
-- continuam existindo como rede — para o caso de alguém escrever no banco por
-- fora da aplicação.
CREATE FUNCTION ledger_assert_bucket_sign() RETURNS trigger AS $$
DECLARE v_saldo bigint;
BEGIN
  IF NEW.bucket = 'SYSTEM' THEN RETURN NULL; END IF;

  SELECT COALESCE(SUM(CASE WHEN direction = 'CREDIT' THEN amount_cents
                           ELSE -amount_cents END), 0)
    INTO v_saldo
    FROM ledger_entries
   WHERE account_id = NEW.account_id AND bucket = NEW.bucket;

  IF NEW.bucket = 'DEBT' THEN
    IF v_saldo > 0 THEN
      RAISE EXCEPTION 'DEBT positivo em % (saldo=%): compensacao maior que a divida (RF-070)',
        NEW.account_id, v_saldo;
    END IF;
  ELSIF v_saldo < 0 THEN
    RAISE EXCEPTION 'Bucket % negativo em % (saldo=%) (RNF-032)',
      NEW.bucket, NEW.account_id, v_saldo;
  END IF;
  RETURN NULL;
END $$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_ledger_bucket_sign
  AFTER INSERT ON ledger_entries
  DEFERRABLE INITIALLY DEFERRED
  FOR EACH ROW EXECUTE FUNCTION ledger_assert_bucket_sign();


-- =====================================================================
-- === V012__ledger_checkpoints.sql ===
-- =====================================================================

CREATE TABLE ledger_checkpoints (
  account_id     uuid NOT NULL,
  bucket         text NOT NULL,
  up_to_entry_id bigint NOT NULL,
  balance_cents  bigint NOT NULL,
  updated_at     timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (account_id, bucket)
);

-- [FIX-B2] Consolidação sob o MESMO bloqueio consultivo usado por toda escrita
-- no razão daquela conta. Enquanto o bloqueio é mantido, não existe lançamento
-- em voo para essa conta — logo max(id) é fronteira segura.
--
-- Sem isto, bigserial atribui o id ANTES do commit e uma entrada com id menor
-- pode confirmar depois do checkpoint fechar. Ela nunca mais entra na soma:
-- nem no checkpoint, nem no "WHERE id > up_to_entry_id".
CREATE FUNCTION ledger_consolidate_account(p_account uuid) RETURNS void AS $$
BEGIN
  PERFORM pg_advisory_xact_lock(4210, hashtext(p_account::text));

  INSERT INTO ledger_checkpoints (account_id, bucket, up_to_entry_id, balance_cents, updated_at)
  SELECT e.account_id,
         e.bucket,
         max(e.id),
         COALESCE(SUM(CASE WHEN e.direction = 'CREDIT' THEN e.amount_cents
                           ELSE -e.amount_cents END), 0),
         now()
  FROM ledger_entries e
  WHERE e.account_id = p_account
  GROUP BY e.account_id, e.bucket
  ON CONFLICT (account_id, bucket) DO UPDATE
    SET up_to_entry_id = EXCLUDED.up_to_entry_id,
        balance_cents  = EXCLUDED.balance_cents,
        updated_at     = now();
END $$ LANGUAGE plpgsql;

-- Reconstrução total: o conserto padrão depois de qualquer incidente
-- (documento 3, §5.2, passo 7).
CREATE FUNCTION ledger_rebuild_checkpoints(p_account uuid DEFAULT NULL) RETURNS void AS $$
DECLARE a uuid;
BEGIN
  IF p_account IS NULL THEN
    FOR a IN SELECT DISTINCT account_id FROM ledger_entries LOOP
      PERFORM ledger_consolidate_account(a);
    END LOOP;
  ELSE
    PERFORM ledger_consolidate_account(p_account);
  END IF;
END $$ LANGUAGE plpgsql;


-- =====================================================================
-- === V013__bank_accounts.sql ===
-- =====================================================================

CREATE TABLE bank_accounts (
  id              uuid PRIMARY KEY,
  account_id      uuid NOT NULL REFERENCES accounts(id),
  bank_code       text NOT NULL,
  branch          text NOT NULL,
  number_enc      bytea NOT NULL,
  number_last4    text NOT NULL,
  holder_tax_id   text NOT NULL,
  pix_key_enc     bytea,
  verified_at     timestamptz,
  archived_at     timestamptz,
  created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON bank_accounts (account_id) WHERE archived_at IS NULL;

-- [FIX-D3] RF-068 era comentário. Agora é restrição.
CREATE FUNCTION bank_account_holder_matches() RETURNS trigger AS $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM accounts a
                 WHERE a.id = NEW.account_id AND a.tax_id = NEW.holder_tax_id) THEN
    RAISE EXCEPTION 'Conta bancaria precisa ser do mesmo CPF/CNPJ do titular (RF-068)';
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_bank_holder BEFORE INSERT OR UPDATE ON bank_accounts
  FOR EACH ROW EXECUTE FUNCTION bank_account_holder_matches();


-- =====================================================================
-- === V014__payouts.sql ===
-- =====================================================================

CREATE TABLE payouts (
  id                  uuid PRIMARY KEY,
  account_id          uuid NOT NULL REFERENCES accounts(id),
  amount_cents        bigint NOT NULL CHECK (amount_cents >= 200),
  bank_account_id     uuid NOT NULL REFERENCES bank_accounts(id),
  status              text NOT NULL DEFAULT 'REQUESTED'
                        CHECK (status IN ('REQUESTED','SENT','CONFIRMED','FAILED')),
  provider_transfer_id text,
  idempotency_key     text NOT NULL,
  created_at          timestamptz NOT NULL DEFAULT now()
);
-- [FIX] escopo por conta, não global (mesma razão do índice de orders)
CREATE UNIQUE INDEX uq_payouts_idem ON payouts (account_id, idempotency_key);
CREATE INDEX ON payouts (account_id, created_at DESC);

-- [FIX-D09] O defeito mais grave encontrado nesta passada: nada ligava
-- payouts.account_id a bank_accounts.account_id. Um saque da conta A para a
-- conta bancária de B era aceito pelo banco — dinheiro saindo para o titular
-- errado, que é a classe AM-12 (acesso a recurso de outra conta) no caminho
-- onde ela custa mais caro. Verificado antes da correção: aceito (H9).
CREATE FUNCTION payout_bank_account_belongs() RETURNS trigger AS $$
DECLARE v_owner uuid; v_verified timestamptz; v_archived timestamptz;
BEGIN
  SELECT account_id, verified_at, archived_at
    INTO v_owner, v_verified, v_archived
    FROM bank_accounts WHERE id = NEW.bank_account_id;

  IF v_owner IS DISTINCT FROM NEW.account_id THEN
    RAISE EXCEPTION 'Conta bancaria % nao pertence a conta % (RF-068)',
      NEW.bank_account_id, NEW.account_id;
  END IF;
  IF v_verified IS NULL THEN
    RAISE EXCEPTION 'Conta bancaria % nao verificada', NEW.bank_account_id;
  END IF;
  IF v_archived IS NOT NULL THEN
    RAISE EXCEPTION 'Conta bancaria % arquivada', NEW.bank_account_id;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_payout_bank_owner BEFORE INSERT OR UPDATE ON payouts
  FOR EACH ROW EXECUTE FUNCTION payout_bank_account_belongs();


-- =====================================================================
-- === V015__disputes_evidence.sql ===
-- =====================================================================

CREATE TABLE disputes (
  id                  uuid PRIMARY KEY,
  charge_id           uuid NOT NULL REFERENCES charges(id),
  -- [FIX-D10] REFUND saiu daqui. Desde que `refunds` passou a existir (V025),
  -- o mesmo reembolso podia ser gravado em duas entidades, com dois estados
  -- divergentes e nenhuma delas autoritativa (H5). Disputa é contestação.
  kind                text NOT NULL DEFAULT 'CHARGEBACK' CHECK (kind = 'CHARGEBACK'),
  reason              text,
  amount_cents        bigint NOT NULL CHECK (amount_cents > 0),
  acquirer_fee_cents  bigint NOT NULL DEFAULT 0 CHECK (acquirer_fee_cents >= 0),
  status              text NOT NULL
                        CHECK (status IN ('OPEN','DEFENDED','WON','LOST')),
  deadline_at         timestamptz,
  provider_dispute_id text UNIQUE,
  created_at          timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_disputes_open ON disputes (charge_id, kind)
  WHERE status NOT IN ('WON','LOST');

CREATE TABLE sale_evidence (
  charge_id           uuid PRIMARY KEY REFERENCES charges(id),
  ip                  inet,
  user_agent          text,
  device_key          text,
  terms_hash          text NOT NULL,
  terms_accepted_at   timestamptz NOT NULL,
  three_ds_result     text,                                         -- RF-074
  email_delivered_at  timestamptz,
  email_opened_at     timestamptz,
  access_log          jsonb NOT NULL DEFAULT '[]'
);


-- =====================================================================
-- === V016__account_risk.sql ===
-- =====================================================================

CREATE TABLE account_risk (
  account_id        uuid PRIMARY KEY REFERENCES accounts(id),
  volume_30d_cents  bigint NOT NULL DEFAULT 0,
  chargeback_bps    int NOT NULL DEFAULT 0,
  refund_bps        int NOT NULL DEFAULT 0,
  tier              int NOT NULL DEFAULT 0 CHECK (tier BETWEEN 0 AND 3),
  tier_changed_at   timestamptz,
  computed_at       timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE risk_events (
  id            uuid PRIMARY KEY,
  account_id    uuid NOT NULL REFERENCES accounts(id),
  kind          text NOT NULL CHECK (kind IN
                  ('ALERT','LIMIT','SUSPEND','TIER_UP','TIER_DOWN','DEBT_REVIEW')),
  metric        text,
  value_bps     int,
  threshold_bps int,
  reason        text NOT NULL,                                      -- RF-078
  notified_at   timestamptz,
  created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON risk_events (account_id, created_at DESC);

-- Índice agregado da plataforma (RF-106), materializado pelo job diário.
CREATE TABLE platform_risk_index (
  computed_on     date PRIMARY KEY,
  chargeback_bps  int NOT NULL,
  refund_bps      int NOT NULL,
  volume_cents    bigint NOT NULL,
  computed_at     timestamptz NOT NULL DEFAULT now()
);


-- =====================================================================
-- === V017__api_keys.sql ===
-- =====================================================================

CREATE TABLE api_keys (
  id           uuid PRIMARY KEY,
  account_id   uuid NOT NULL REFERENCES accounts(id),
  name         text NOT NULL,
  -- [FIX-D2] HMAC-SHA256 com pepper no cofre, NÃO Argon2id.
  -- Argon2id é salgado por linha: não dá para procurar a chave a partir do
  -- segredo apresentado, e verificar linha a linha custa 50–200 ms por
  -- requisição, contra os 800 ms p95 inteiros do RNF-002.
  key_hash     text NOT NULL UNIQUE,          -- hex do HMAC
  prefix       text NOT NULL UNIQUE,          -- exibição e busca em O(1)
  scopes       text[] NOT NULL DEFAULT '{}',
  last_used_at timestamptz,
  revoked_at   timestamptz,
  created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON api_keys (account_id) WHERE revoked_at IS NULL;


-- =====================================================================
-- === V018__webhook_endpoints.sql ===
-- =====================================================================

CREATE TABLE webhook_endpoints (                                    -- RF-109
  id                uuid PRIMARY KEY,
  account_id        uuid NOT NULL REFERENCES accounts(id),
  url               text NOT NULL,
  secret_enc        bytea NOT NULL,
  secret_prev_enc   bytea,
  secret_rotated_at timestamptz,
  event_types       text[] NOT NULL DEFAULT '{}',
  disabled_at       timestamptz,
  created_at        timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON webhook_endpoints (account_id) WHERE disabled_at IS NULL;


-- =====================================================================
-- === V019__outbox.sql ===
-- =====================================================================

CREATE TABLE outbox_events (
  id           uuid PRIMARY KEY,
  account_id   uuid NOT NULL,
  event_type   text NOT NULL,
  payload      jsonb NOT NULL,
  created_at   timestamptz NOT NULL DEFAULT now(),
  published_at timestamptz
);
CREATE INDEX ON outbox_events (created_at) WHERE published_at IS NULL;

CREATE TABLE webhook_deliveries (
  id           uuid PRIMARY KEY,
  event_id     uuid NOT NULL REFERENCES outbox_events(id),
  endpoint_id  uuid NOT NULL REFERENCES webhook_endpoints(id),
  attempt      int NOT NULL,
  status_code  int,
  error        text,
  next_retry_at timestamptz,
  created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON webhook_deliveries (next_retry_at) WHERE next_retry_at IS NOT NULL;
CREATE INDEX ON webhook_deliveries (event_id);


-- =====================================================================
-- === V020__idempotency_keys.sql ===
-- =====================================================================

CREATE TABLE idempotency_keys (
  scope        text NOT NULL,
  key          text NOT NULL,
  request_hash text NOT NULL,
  response     jsonb,
  status       text NOT NULL CHECK (status IN ('IN_FLIGHT','DONE')),
  expires_at   timestamptz NOT NULL,
  created_at   timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (scope, key)
);
CREATE INDEX ON idempotency_keys (expires_at);


-- =====================================================================
-- === V021__fiscal_profiles_invoices.sql ===
-- =====================================================================

CREATE TABLE fiscal_profiles (                                      -- RF-095
  account_id        uuid PRIMARY KEY REFERENCES accounts(id),
  municipality_code text NOT NULL,
  municipal_reg     text,
  service_code      text NOT NULL,
  iss_bps           int NOT NULL CHECK (iss_bps BETWEEN 0 AND 500),
  tax_regime        text NOT NULL CHECK (tax_regime IN ('SIMPLES','PRESUMIDO','REAL')),
  credential_ref    text NOT NULL,                                  -- referência no cofre
  validated_at      timestamptz,
  created_at        timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE invoices (
  id             uuid PRIMARY KEY,
  charge_id      uuid NOT NULL REFERENCES charges(id),
  issuer_id      uuid NOT NULL REFERENCES accounts(id),             -- o VENDEDOR emite
  status         text NOT NULL DEFAULT 'QUEUED'
                   CHECK (status IN ('QUEUED','ISSUED','FAILED','CANCEL_REQUESTED',
                                     'CANCELED','CANCEL_FAILED')),
  provider_ref   text,
  number         text,
  pdf_url        text,
  amount_cents   bigint NOT NULL CHECK (amount_cents > 0),
  error          text,
  attempt_count  int NOT NULL DEFAULT 0,
  next_retry_at  timestamptz,
  issued_at      timestamptz,
  created_at     timestamptz NOT NULL DEFAULT now()
);
-- [FIX-D11] issuer_id era um uuid livre apontando para accounts. A nota podia
-- sair em nome de qualquer conta — inclusive a do afiliado (H11). Quem presta
-- o serviço é o vendedor do produto, e é ele quem responde pelo ISS (PEN-19).
CREATE FUNCTION invoice_issuer_is_seller() RETURNS trigger AS $$
DECLARE v_seller uuid;
BEGIN
  SELECT p.seller_id INTO v_seller
    FROM charges c
    JOIN orders   o ON o.id = c.order_id
    JOIN offers   f ON f.id = o.offer_id
    JOIN products p ON p.id = f.product_id
   WHERE c.id = NEW.charge_id;

  IF v_seller IS DISTINCT FROM NEW.issuer_id THEN
    RAISE EXCEPTION 'NFS-e precisa ser emitida em nome do vendedor do produto (%), nao de %',
      v_seller, NEW.issuer_id;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_invoice_issuer BEFORE INSERT OR UPDATE ON invoices
  FOR EACH ROW EXECUTE FUNCTION invoice_issuer_is_seller();

CREATE UNIQUE INDEX uq_invoice_per_charge ON invoices (charge_id) WHERE status <> 'FAILED';
CREATE INDEX ON invoices (next_retry_at) WHERE status IN ('QUEUED','CANCEL_REQUESTED');


-- =====================================================================
-- === V022__platform_subscriptions.sql ===
-- =====================================================================

-- [FIX-C4] Fonte única do plano comercial da conta.
CREATE TABLE platform_subscriptions (                               -- RF-102
  account_id            uuid PRIMARY KEY REFERENCES accounts(id),
  plan                  text NOT NULL DEFAULT 'TRANSACIONAL'
                          CHECK (plan IN ('TRANSACIONAL','ESCALA')),
  price_cents           bigint NOT NULL DEFAULT 0 CHECK (price_cents >= 0),
  current_period_start  timestamptz NOT NULL,
  current_period_end    timestamptz NOT NULL,
  status                text NOT NULL DEFAULT 'ACTIVE'
                          CHECK (status IN ('ACTIVE','PAST_DUE','DOWNGRADED')),
  past_due_since        timestamptz,
  CHECK (current_period_end > current_period_start)
);

-- [FIX-D12] FIX-C4 tirou `plan` de accounts e elegeu platform_subscriptions
-- como fonte única — mas nada garantia que a linha existisse. Sete das oito
-- contas da massa de teste ficaram sem plano (H10), e uma conta sem plano é
-- uma cobrança sem tabela de preço. O padrão nasce com a conta.
CREATE FUNCTION account_default_plan() RETURNS trigger AS $$
BEGIN
  INSERT INTO platform_subscriptions
    (account_id, plan, price_cents, current_period_start, current_period_end)
  VALUES (NEW.id, 'TRANSACIONAL', 0, now(), now() + interval '1 month')
  ON CONFLICT (account_id) DO NOTHING;
  RETURN NULL;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_account_default_plan AFTER INSERT ON accounts
  FOR EACH ROW EXECUTE FUNCTION account_default_plan();

CREATE TABLE plan_changes (                                         -- RF-114
  id          uuid PRIMARY KEY,
  account_id  uuid NOT NULL REFERENCES accounts(id),
  from_plan   text,
  to_plan     text NOT NULL,
  changed_by  uuid,                     -- accounts.id ou admin_users.id
  price_table text NOT NULL,            -- identificador da tabela vigente
  created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON plan_changes (account_id, created_at DESC);


-- =====================================================================
-- === V023__admin_users_audit.sql ===
-- =====================================================================

CREATE TABLE admin_users (
  id            uuid PRIMARY KEY,
  email         citext NOT NULL UNIQUE,
  password_hash text NOT NULL,
  role          text NOT NULL CHECK (role IN ('SUPPORT','RISK','COMPLIANCE','ADMIN')),
  mfa_enforced  boolean NOT NULL DEFAULT true,                      -- RNF-022
  disabled_at   timestamptz,
  created_at    timestamptz NOT NULL DEFAULT now()
);

-- [FIX-A4] mfa_credentials referencia accounts(id). Operador interno não é
-- conta de usuário, logo não tinha onde guardar o segundo fator — e o item 19
-- da lista de lançamento é bloqueante.
CREATE TABLE admin_mfa_credentials (
  admin_id      uuid PRIMARY KEY REFERENCES admin_users(id),
  secret_enc    bytea NOT NULL,
  confirmed_at  timestamptz,
  recovery_hash text[] NOT NULL DEFAULT '{}',
  created_at    timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE admin_audit_log (                                      -- RNF-021
  id           bigserial PRIMARY KEY,
  admin_id     uuid NOT NULL REFERENCES admin_users(id),
  action       text NOT NULL,
  target_type  text NOT NULL,
  target_id    text NOT NULL,
  reason       text NOT NULL,
  before_state jsonb,
  after_state  jsonb,
  ip           inet,
  created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON admin_audit_log (target_type, target_id, created_at DESC);

CREATE TRIGGER trg_audit_no_update BEFORE UPDATE ON admin_audit_log
  FOR EACH ROW EXECUTE FUNCTION ledger_is_append_only();
CREATE TRIGGER trg_audit_no_delete BEFORE DELETE ON admin_audit_log
  FOR EACH ROW EXECUTE FUNCTION ledger_is_append_only();

REVOKE UPDATE, DELETE ON admin_audit_log FROM paysi_app;


-- =====================================================================
-- === V024__lgpd_requests.sql ===
-- =====================================================================

CREATE TABLE lgpd_requests (                                        -- RF-115
  id           uuid PRIMARY KEY,
  subject_kind text NOT NULL CHECK (subject_kind IN ('BUYER','ACCOUNT')),
  subject_ref  text NOT NULL,
  kind         text NOT NULL CHECK (kind IN ('ACCESS','DELETION','CORRECTION','PORTABILITY')),
  status       text NOT NULL DEFAULT 'OPEN'
                 CHECK (status IN ('OPEN','IN_PROGRESS','DONE','REJECTED')),
  due_at       timestamptz NOT NULL,                                -- RNF-027
  handled_by   uuid REFERENCES admin_users(id),
  resolution   text,
  created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON lgpd_requests (due_at) WHERE status IN ('OPEN','IN_PROGRESS');


-- =====================================================================
-- === V025__refunds.sql ===
-- =====================================================================

-- [FIX-C3] RF-105 (reembolso parcial) não tinha entidade: o pedido só sabia
-- dizer REFUNDED. Cada reembolso é um objeto próprio, como em toda plataforma
-- de pagamento; o acumulador em charges/orders é a projeção.
CREATE TABLE refunds (
  id                 uuid PRIMARY KEY,
  charge_id          uuid NOT NULL REFERENCES charges(id),
  amount_cents       bigint NOT NULL CHECK (amount_cents > 0),
  -- [FIX-B8] repartição por parte, gravada em cada reembolso.
  -- Reembolso parcial é o TERCEIRO ponto de truncamento do sistema, e não
  -- tinha regra escrita. Cada parcial trunca proporcionalmente; o ÚLTIMO
  -- (o que completa o total) liquida exatamente o que resta da alocação
  -- original. Sem isso, a soma dos parciais não fecha com a venda.
  seller_cents       bigint NOT NULL CHECK (seller_cents >= 0),
  affiliate_cents    bigint NOT NULL DEFAULT 0 CHECK (affiliate_cents >= 0),
  platform_cents     bigint NOT NULL CHECK (platform_cents >= 0),
  provider_cents     bigint NOT NULL DEFAULT 0 CHECK (provider_cents >= 0),
  reason             text,
  status             text NOT NULL DEFAULT 'PENDING'
                       CHECK (status IN ('PENDING','SUCCEEDED','FAILED')),
  provider_refund_id text UNIQUE,
  idempotency_key    text NOT NULL,
  requested_by       text NOT NULL CHECK (requested_by IN ('BUYER','SELLER','ADMIN','SYSTEM')),
  created_at         timestamptz NOT NULL DEFAULT now(),
  settled_at         timestamptz,
  UNIQUE (charge_id, idempotency_key),
  CHECK (seller_cents + affiliate_cents + platform_cents + provider_cents = amount_cents)
);
CREATE INDEX ON refunds (charge_id);


-- =====================================================================
-- === V026__ledger_adjustments.sql ===
-- =====================================================================

-- [FIX-B10] ADJUSTMENT e DEBT_WRITEOFF eram tipos declarados no CHECK do
-- razão sem nada por trás: reference_id não apontava para lugar nenhum e a
-- aprovação exigida por RF-116 e pelo doc 4 §5 não tinha onde ser registrada.
CREATE TABLE ledger_adjustments (
  id            uuid PRIMARY KEY,
  kind          text NOT NULL CHECK (kind IN ('ADJUSTMENT','DEBT_WRITEOFF')),
  account_id    uuid NOT NULL REFERENCES accounts(id),
  bucket        text NOT NULL,
  direction     text NOT NULL CHECK (direction IN ('DEBIT','CREDIT')),
  amount_cents  bigint NOT NULL CHECK (amount_cents > 0),
  reason        text NOT NULL,
  requested_by  uuid NOT NULL REFERENCES admin_users(id),
  approved_by   uuid REFERENCES admin_users(id),
  auto_approved boolean NOT NULL DEFAULT false,
  approved_at   timestamptz,
  status        text NOT NULL DEFAULT 'PENDING_APPROVAL'
                  CHECK (status IN ('PENDING_APPROVAL','APPROVED','REJECTED','APPLIED')),
  created_at    timestamptz NOT NULL DEFAULT now(),
  -- Segregação do doc 4 §5, no banco: quem aprova não é quem pede.
  -- Abaixo do limiar de valor, auto_approved dispensa a segunda assinatura —
  -- que é o arranjo possível enquanto a equipe for de uma pessoa.
  CONSTRAINT segregacao_de_funcao CHECK (
    status <> 'APPROVED' OR auto_approved
    OR (approved_by IS NOT NULL AND approved_by <> requested_by)
  )
);
CREATE INDEX ON ledger_adjustments (status) WHERE status = 'PENDING_APPROVAL';
CREATE INDEX ON ledger_adjustments (account_id, created_at DESC);


-- =====================================================================
-- === V027__provider_events.sql ===
-- =====================================================================

-- [FIX-B1] Padrão inbox — contrapartida do outbox do ADR-07.
-- O provedor também entrega "ao menos uma vez". Sem esta tabela, a segunda
-- entrega de payment.confirmed escreve uma segunda transação SALE que soma
-- zero, passa nas três verificações e credita o vendedor duas vezes.
CREATE TABLE provider_events (
  provider           text NOT NULL,
  provider_event_id  text NOT NULL,
  event_type         text NOT NULL,
  payload            jsonb NOT NULL,
  signature_valid    boolean NOT NULL,
  received_at        timestamptz NOT NULL DEFAULT now(),
  processed_at       timestamptz,
  status             text NOT NULL DEFAULT 'RECEIVED'
                       CHECK (status IN ('RECEIVED','PROCESSED','IGNORED','FAILED')),
  error              text,
  attempt_count      int NOT NULL DEFAULT 0,
  next_retry_at      timestamptz,
  PRIMARY KEY (provider, provider_event_id)
);
CREATE INDEX ON provider_events (status, next_retry_at) WHERE status IN ('RECEIVED','FAILED');
CREATE INDEX ON provider_events (received_at);


-- =====================================================================
-- === V028__integrity_views.sql ===
-- =====================================================================

-- As verificações passam de três para cinco. Resultado não vazio em qualquer
-- uma é incidente de severidade máxima (documento 3, §5.1).

-- 1. Toda transação soma zero (RNF-014)
CREATE VIEW v_check_unbalanced_transactions AS
SELECT transaction_id,
       SUM(CASE WHEN direction = 'CREDIT' THEN amount_cents ELSE -amount_cents END) AS desvio
FROM ledger_entries
GROUP BY transaction_id
HAVING SUM(CASE WHEN direction = 'CREDIT' THEN amount_cents ELSE -amount_cents END) <> 0;

-- 2. [FIX-A1] Nenhum bucket de USUÁRIO negativo, exceto DEBT (RNF-032).
--    SYSTEM está fora: SYS_CLEARING é debitado em toda venda e é negativo
--    por construção. A consulta original acusaria incidente todo dia.
CREATE VIEW v_check_negative_user_buckets AS
SELECT account_id, bucket,
       SUM(CASE WHEN direction = 'CREDIT' THEN amount_cents ELSE -amount_cents END) AS saldo
FROM ledger_entries
WHERE bucket NOT IN ('DEBT','SYSTEM')
GROUP BY account_id, bucket
HAVING SUM(CASE WHEN direction = 'CREDIT' THEN amount_cents ELSE -amount_cents END) < 0;

-- 3. DEBT nunca positivo (RF-070)
CREATE VIEW v_check_positive_debt AS
SELECT account_id,
       SUM(CASE WHEN direction = 'CREDIT' THEN amount_cents ELSE -amount_cents END) AS saldo
FROM ledger_entries
WHERE bucket = 'DEBT'
GROUP BY account_id
HAVING SUM(CASE WHEN direction = 'CREDIT' THEN amount_cents ELSE -amount_cents END) > 0;

-- 4. [FIX-A1 / nova] Conta de sistema com sinal contrário ao saldo normal
--    declarado. É a verificação que a nº 2 deixou de fazer, feita direito.
CREATE VIEW v_check_system_sign_violation AS
SELECT la.code, la.normal_balance,
       SUM(CASE WHEN e.direction = 'CREDIT' THEN e.amount_cents ELSE -e.amount_cents END) AS saldo
FROM ledger_entries e
JOIN ledger_accounts la ON la.id = e.account_id
WHERE e.bucket = 'SYSTEM'
GROUP BY la.code, la.normal_balance
HAVING (la.normal_balance = 'DEBIT'  AND SUM(CASE WHEN e.direction='CREDIT' THEN e.amount_cents ELSE -e.amount_cents END) > 0)
    OR (la.normal_balance = 'CREDIT' AND SUM(CASE WHEN e.direction='CREDIT' THEN e.amount_cents ELSE -e.amount_cents END) < 0);

-- 5. [FIX-B2 / nova] Deriva do resumo de saldo.
--    Compara o checkpoint com a soma real ATÉ o mesmo up_to_entry_id.
--    Detecta o lançamento que confirmou depois da fronteira — que é
--    invisível para todas as outras verificações.
CREATE VIEW v_check_checkpoint_drift AS
SELECT c.account_id, c.bucket, c.up_to_entry_id,
       c.balance_cents AS saldo_checkpoint,
       t.saldo_real,
       t.saldo_real - c.balance_cents AS desvio
FROM ledger_checkpoints c
CROSS JOIN LATERAL (
  SELECT COALESCE(SUM(CASE WHEN e.direction = 'CREDIT' THEN e.amount_cents
                           ELSE -e.amount_cents END), 0) AS saldo_real
  FROM ledger_entries e
  WHERE e.account_id = c.account_id
    AND e.bucket     = c.bucket
    AND e.id        <= c.up_to_entry_id
) t
WHERE t.saldo_real <> c.balance_cents;


-- 6. [FIX-D05 / nova] Cronograma de recebíveis incoerente com a cobrança.
--    O rateio por parcela é o segundo ponto de truncamento do sistema e não
--    tinha nenhuma verificação: a soma das parcelas podia não fechar com a
--    cobrança e ninguém saberia até a liberação pagar a mais ou a menos.
CREATE VIEW v_check_receivable_schedule AS
SELECT c.id AS charge_id,
       c.amount_cents,
       SUM(r.amount_cents)            AS soma_parcelas,
       c.seller_amount_cents,
       SUM(r.seller_amount_cents)     AS soma_vendedor,
       c.affiliate_fee_cents,
       SUM(r.affiliate_amount_cents)  AS soma_afiliado
FROM charges c
JOIN receivables r ON r.charge_id = c.id
GROUP BY c.id, c.amount_cents, c.seller_amount_cents, c.affiliate_fee_cents
HAVING SUM(r.amount_cents)           <> c.amount_cents
    OR SUM(r.seller_amount_cents)    <> c.seller_amount_cents
    OR SUM(r.affiliate_amount_cents) <> c.affiliate_fee_cents;

-- 7. [FIX-D13 / nova] Acumulador de reembolso divergindo da soma dos reembolsos.
--    charges.refunded_cents é campo mutável mantido pela aplicação; refunds é
--    o fato. Divergir significa reembolsar acima do valor da venda sem que
--    nenhuma restrição perceba (H4).
CREATE VIEW v_check_refund_accumulator AS
SELECT c.id AS charge_id, c.amount_cents, c.refunded_cents,
       COALESCE(SUM(r.amount_cents), 0) AS soma_reembolsos
FROM charges c
LEFT JOIN refunds r ON r.charge_id = c.id AND r.status = 'SUCCEEDED'
GROUP BY c.id, c.amount_cents, c.refunded_cents
HAVING COALESCE(SUM(r.amount_cents), 0) <> c.refunded_cents
    OR COALESCE(SUM(r.amount_cents), 0) > c.amount_cents;

-- 8. [FIX-D07 / nova] Lançamento agendado sem linha de agendamento, ou linha
--    de agendamento vencida e nunca executada. A primeira metade virou
--    impossível com o gatilho; a segunda é operacional e precisa de alarme.
CREATE VIEW v_check_release_schedule AS
SELECT e.id AS entry_id, e.account_id, e.bucket, e.release_at,
       CASE WHEN s.entry_id IS NULL THEN 'SEM_AGENDAMENTO'
            ELSE 'VENCIDO_NAO_EXECUTADO' END AS problema
FROM ledger_entries e
LEFT JOIN ledger_release_schedule s ON s.entry_id = e.id
WHERE e.release_at IS NOT NULL
  AND (s.entry_id IS NULL
       OR (s.released_at IS NULL AND s.release_at < now() - interval '2 hours'));

-- Estado consolidado do pedido, derivado das cobranças ([FIX-D04]).
-- Substitui as colunas de reembolso que saíram de `orders`.
CREATE VIEW v_order_status AS
SELECT o.id AS order_id,
       o.status                              AS lifecycle_status,
       COALESCE(SUM(c.amount_cents), 0)      AS cobrado_cents,
       COALESCE(SUM(c.refunded_cents), 0)    AS devolvido_cents,
       CASE
         WHEN bool_or(c.status = 'CHARGEBACK')                          THEN 'CHARGEBACK'
         WHEN COALESCE(SUM(c.refunded_cents),0) = 0                     THEN o.status
         WHEN SUM(c.refunded_cents) >= SUM(c.amount_cents)              THEN 'REFUNDED'
         ELSE 'PARTIALLY_REFUNDED'
       END AS status_consolidado
FROM orders o
LEFT JOIN charges c ON c.order_id = o.id
GROUP BY o.id, o.status;


-- =====================================================================
-- === V029__system_accounts_seed.sql ===
-- =====================================================================

-- Convenção de sinal deste razão: saldo = créditos − débitos.
-- SYS_CLEARING é a ORIGEM do dinheiro que entra do provedor: é debitada em
-- toda venda e acumula saldo negativo. Contas de destino acumulam positivo.
INSERT INTO ledger_accounts (id, code, name, normal_balance) VALUES
  ('00000000-0000-0000-0000-0000000000c1','SYS_CLEARING',
   'Compensacao do provedor',            'DEBIT'),
  ('00000000-0000-0000-0000-0000000000c2','SYS_PLATFORM_REVENUE',
   'Receita da plataforma',              'CREDIT'),
  ('00000000-0000-0000-0000-0000000000c3','SYS_PROVIDER_FEE',
   'Custo do provedor',                  'CREDIT'),
  ('00000000-0000-0000-0000-0000000000c4','SYS_REFUND_LOSS',
   'Perda absorvida em reembolso',       'DEBIT'),
  ('00000000-0000-0000-0000-0000000000c5','SYS_CHARGEBACK_LOSS',
   'Perda residual em contestacao',      'DEBIT'),
  -- [FIX-B7] Conta que faltava. O lançamento de contestação do doc 2 §3.6
  -- creditava a tarifa da adquirente (R$ 30,00) em SYS_CLEARING, como se o
  -- provedor tivesse devolvido um dinheiro que ele nunca devolveu.
  -- A transação soma zero e passa nas três verificações originais — mas
  -- SYS_CLEARING deixa de bater com o extrato do provedor, que é exatamente
  -- o que a conciliação diária do RF-089 compara.
  ('00000000-0000-0000-0000-0000000000c6','SYS_ACQUIRER_FEE',
   'Tarifa retida pela adquirente',      'CREDIT')
ON CONFLICT (code) DO NOTHING;
