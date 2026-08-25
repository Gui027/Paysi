\set ON_ERROR_STOP off
\pset pager off
SET client_min_messages = NOTICE;

-- =====================================================================
-- Infraestrutura de teste
-- =====================================================================
CREATE OR REPLACE FUNCTION t_ok(nome text, cond boolean) RETURNS void AS $$
BEGIN
  RAISE NOTICE '%  %', CASE WHEN cond THEN 'PASS' ELSE '*** FAIL ***' END, nome;
END $$ LANGUAGE plpgsql;

-- executa SQL esperando exceção
CREATE OR REPLACE FUNCTION t_raises(nome text, sql text) RETURNS void AS $$
BEGIN
  BEGIN
    EXECUTE sql;
    RAISE NOTICE '*** FAIL ***  % (nao levantou excecao)', nome;
  EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'PASS  % [%]', nome, left(SQLERRM, 40);
  END;
END $$ LANGUAGE plpgsql;

-- executa SQL esperando exceção, inclusive de gatilho DEFERIDO
CREATE OR REPLACE FUNCTION t_raises_def(nome text, sql text) RETURNS void AS $$
BEGIN
  BEGIN
    EXECUTE sql;
    EXECUTE 'SET CONSTRAINTS ALL IMMEDIATE';
    RAISE NOTICE '*** FAIL ***  % (nao levantou excecao)', nome;
  EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'PASS  % [%]', nome, left(SQLERRM, 45);
  END;
END $$ LANGUAGE plpgsql;

-- grava uma transação contábil validando partidas dobradas
CREATE OR REPLACE FUNCTION t_tx(p_type text, p_reftype text, p_refid text,
                                p_desc text, p_entries jsonb) RETURNS uuid AS $$
DECLARE
  v_tx uuid := gen_random_uuid();
  e jsonb; v_soma bigint := 0;
BEGIN
  FOR e IN SELECT * FROM jsonb_array_elements(p_entries) LOOP
    v_soma := v_soma + CASE WHEN e->>'d' = 'CREDIT'
                            THEN (e->>'v')::bigint ELSE -(e->>'v')::bigint END;
  END LOOP;
  IF v_soma <> 0 THEN
    RAISE EXCEPTION 'Transacao nao soma zero: desvio=%', v_soma;
  END IF;

  INSERT INTO ledger_transactions (id, type, reference_type, reference_id, description)
  VALUES (v_tx, p_type, p_reftype, p_refid, p_desc);

  FOR e IN SELECT * FROM jsonb_array_elements(p_entries) LOOP
    INSERT INTO ledger_entries (transaction_id, account_id, bucket, direction,
                                amount_cents, origin, release_at)
    VALUES (v_tx,
            CASE WHEN e->>'b' = 'SYSTEM'
                 THEN (SELECT id FROM ledger_accounts WHERE code = e->>'a')
                 ELSE (e->>'a')::uuid END,
            e->>'b', e->>'d', (e->>'v')::bigint,
            COALESCE(e->>'o','OTHER'),
            CASE WHEN e ? 'r' THEN (e->>'r')::timestamptz END);
  END LOOP;
  RETURN v_tx;
END $$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION saldo(p_account uuid, p_bucket text) RETURNS bigint AS $$
  SELECT COALESCE(SUM(CASE WHEN direction='CREDIT' THEN amount_cents
                           ELSE -amount_cents END), 0)
  FROM ledger_entries WHERE account_id = p_account AND bucket = p_bucket;
$$ LANGUAGE sql;

CREATE OR REPLACE FUNCTION saldo_sys(p_code text) RETURNS bigint AS $$
  SELECT COALESCE(SUM(CASE WHEN e.direction='CREDIT' THEN e.amount_cents
                           ELSE -e.amount_cents END), 0)
  FROM ledger_entries e JOIN ledger_accounts la ON la.id = e.account_id
  WHERE la.code = p_code AND e.bucket = 'SYSTEM';
$$ LANGUAGE sql;

-- =====================================================================
-- Massa: vendedor, afiliado, produto, oferta, comprador, pedido, cobrança
-- =====================================================================
\set V '''11111111-1111-1111-1111-111111111111'''
\set A '''22222222-2222-2222-2222-222222222222'''

INSERT INTO accounts (id,email,password_hash,full_name,person_type,tax_id,kyc_status,status)
VALUES (:V,'vendedor@x.com','h','Vendedor SA','PJ','00000000000191','APPROVED','ACTIVE'),
       (:A,'afiliado@x.com','h','Afiliado ME','PJ','00000000000272','APPROVED','ACTIVE');

-- [D12] a linha já nasce com a conta; aqui só se confirma
SELECT t_ok('D12 plano comercial criado junto com a conta',
  (SELECT count(*)=2 FROM platform_subscriptions));

INSERT INTO products (id,seller_id,name,segment,charge_type,status)
VALUES ('aaaaaaaa-0000-0000-0000-000000000001',:V,'CRM Pro','SAAS','SUBSCRIPTION','ACTIVE');

-- gatilho A3: a aplicação NÃO informa charge_type/segment
INSERT INTO offers (id,product_id,charge_type,segment,slug,amount_cents,cycle,guarantee_days)
VALUES ('bbbbbbbb-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000001',
        'X','X','crm-pro',10000,'MONTHLY',7);

INSERT INTO buyers (id,email,tax_id,person_type,name)
VALUES ('cccccccc-0000-0000-0000-000000000001','marina@x.com','00000000000000','PJ','Estudio ML');

INSERT INTO affiliations (id,product_id,affiliate_id,commission_bps,recurring,status,approved_at)
VALUES ('dddddddd-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000001',
        :A,1000,true,'APPROVED',now());

INSERT INTO orders (id,offer_id,buyer_id,affiliation_id,buyer_snapshot,gross_cents,paid_cents,
                    method,status,idempotency_key,request_hash,confirmed_at)
VALUES ('eeeeeeee-0000-0000-0000-000000000001','bbbbbbbb-0000-0000-0000-000000000001',
        'cccccccc-0000-0000-0000-000000000001','dddddddd-0000-0000-0000-000000000001',
        '{}',10000,10000,'CARD','PAID','idem-1','hash-1',now());

INSERT INTO charges (id,order_id,cycle_number,amount_cents,plan,platform_fee_bps,
                     platform_fee_fixed_cents,platform_fee_cents,affiliate_fee_cents,
                     seller_amount_cents,provider_fee_cents,status,provider_charge_id,paid_at,confirmed_at)
VALUES ('ffffffff-0000-0000-0000-000000000001','eeeeeeee-0000-0000-0000-000000000001',
        1,10000,'TRANSACIONAL',599,200,799,1000,8201,348,'PAID','prov_1',now(),now());

-- =====================================================================
-- BLOCO 1 — gatilhos de catálogo e cadastro
-- =====================================================================
\echo ''
\echo '--- BLOCO 1: gatilhos de catalogo e cadastro ---'

SELECT t_ok('A3 gatilho desnormaliza charge_type/segment da oferta',
  (SELECT charge_type='SUBSCRIPTION' AND segment='SAAS'
     FROM offers WHERE id='bbbbbbbb-0000-0000-0000-000000000001'));

SELECT t_raises('A3 charge_type da oferta e imutavel',
  $$UPDATE offers SET charge_type='ONE_TIME' WHERE slug='crm-pro'$$);

SELECT t_raises('A3 segmento do produto imutavel apos existir oferta',
  $$UPDATE products SET segment='DIGITAL' WHERE id='aaaaaaaa-0000-0000-0000-000000000001'$$);

SELECT t_raises('A3 ciclo imutavel apos venda paga',
  $$UPDATE offers SET cycle='ANNUAL' WHERE slug='crm-pro'$$);

UPDATE offers SET amount_cents=12000 WHERE slug='crm-pro';
SELECT t_ok('A3 preco continua editavel',
  (SELECT amount_cents=12000 FROM offers WHERE slug='crm-pro'));
UPDATE offers SET amount_cents=10000 WHERE slug='crm-pro';

SELECT t_raises('RF-049 autoafiliacao vedada',
  format($$INSERT INTO affiliations (id,product_id,affiliate_id,commission_bps,recurring)
           VALUES (gen_random_uuid(),'aaaaaaaa-0000-0000-0000-000000000001',%L,500,false)$$, :V));

SELECT t_raises('RF-046 comissao imutavel apos aprovacao',
  $$UPDATE affiliations SET commission_bps=2000 WHERE id='dddddddd-0000-0000-0000-000000000001'$$);

SELECT t_raises('D4 boleto vedado em oferta DIGITAL',
  $$INSERT INTO products (id,seller_id,name,segment,charge_type) VALUES
      ('aaaaaaaa-0000-0000-0000-0000000000d1','11111111-1111-1111-1111-111111111111','Ebook','DIGITAL','ONE_TIME');
    INSERT INTO offers (id,product_id,charge_type,segment,slug,amount_cents) VALUES
      ('bbbbbbbb-0000-0000-0000-0000000000d1','aaaaaaaa-0000-0000-0000-0000000000d1','X','X','ebook',5000);
    INSERT INTO offer_payment_methods (offer_id,method) VALUES
      ('bbbbbbbb-0000-0000-0000-0000000000d1','BOLETO')$$);

INSERT INTO offer_payment_methods (offer_id,method)
  VALUES ('bbbbbbbb-0000-0000-0000-000000000001','BOLETO');
SELECT t_ok('D4 boleto permitido em oferta SAAS',
  (SELECT count(*)=1 FROM offer_payment_methods
    WHERE offer_id='bbbbbbbb-0000-0000-0000-000000000001' AND method='BOLETO'));

SELECT t_raises('D3 conta bancaria de outro CPF/CNPJ recusada (RF-068)',
  format($$INSERT INTO bank_accounts (id,account_id,bank_code,branch,number_enc,number_last4,holder_tax_id)
           VALUES (gen_random_uuid(),%L,'001','0001','\x00','1234','99999999999999')$$, :V));

INSERT INTO bank_accounts (id,account_id,bank_code,branch,number_enc,number_last4,holder_tax_id,verified_at)
VALUES ('99999999-0000-0000-0000-000000000001',:V,'001','0001','\x00','1234','00000000000191',now());
SELECT t_ok('D3 conta bancaria do mesmo titular aceita', true);

SELECT t_raises('A2 cupom nao pode derrubar paid_cents abaixo do piso tecnico',
  $$INSERT INTO orders (id,offer_id,buyer_id,buyer_snapshot,gross_cents,discount_cents,paid_cents,
                        method,idempotency_key,request_hash)
    VALUES (gen_random_uuid(),'bbbbbbbb-0000-0000-0000-000000000001',
            'cccccccc-0000-0000-0000-000000000001','{}',10000,9600,400,'PIX','k','h')$$);

INSERT INTO orders (id,offer_id,buyer_id,buyer_snapshot,gross_cents,discount_cents,paid_cents,
                    method,idempotency_key,request_hash)
VALUES ('eeeeeeee-0000-0000-0000-0000000000a2','bbbbbbbb-0000-0000-0000-000000000001',
        'cccccccc-0000-0000-0000-000000000001','{}',10000,9500,500,'PIX','k2','h');
SELECT t_ok('A2 cupom ate o piso tecnico (R$ 5,00) aceito',
  (SELECT paid_cents=500 FROM orders WHERE id='eeeeeeee-0000-0000-0000-0000000000a2'));

SELECT t_raises('C4 invariante da cobranca verificada no banco',
  $$INSERT INTO charges (id,order_id,amount_cents,plan,platform_fee_bps,platform_fee_fixed_cents,
                         platform_fee_cents,affiliate_fee_cents,seller_amount_cents)
    VALUES (gen_random_uuid(),'eeeeeeee-0000-0000-0000-000000000001',10000,'TRANSACIONAL',599,200,799,1000,8000)$$);

SELECT t_raises('B9 mesmo ciclo de assinatura nao pode ser cobrado duas vezes',
  $$INSERT INTO subscriptions (id,order_id,offer_id,status) VALUES
      ('77777777-0000-0000-0000-000000000001','eeeeeeee-0000-0000-0000-000000000001',
       'bbbbbbbb-0000-0000-0000-000000000001','ACTIVE');
    INSERT INTO charges (id,order_id,subscription_id,cycle_number,amount_cents,plan,platform_fee_bps,
                         platform_fee_fixed_cents,platform_fee_cents,affiliate_fee_cents,seller_amount_cents)
    VALUES (gen_random_uuid(),'eeeeeeee-0000-0000-0000-000000000001','77777777-0000-0000-0000-000000000001',
            2,10000,'TRANSACIONAL',599,200,799,1000,8201);
    INSERT INTO charges (id,order_id,subscription_id,cycle_number,amount_cents,plan,platform_fee_bps,
                         platform_fee_fixed_cents,platform_fee_cents,affiliate_fee_cents,seller_amount_cents)
    VALUES (gen_random_uuid(),'eeeeeeee-0000-0000-0000-000000000001','77777777-0000-0000-0000-000000000001',
            2,10000,'TRANSACIONAL',599,200,799,1000,8201)$$);

-- =====================================================================
-- BLOCO 2 — cadeia completa do razão
-- =====================================================================
\echo ''
\echo '--- BLOCO 2: cadeia completa do razao ---'

-- 1. SALE
SELECT t_tx('SALE','CHARGE','ffffffff-0000-0000-0000-000000000001','Venda R$ 100,00',
  format('[{"a":"SYS_CLEARING","b":"SYSTEM","d":"DEBIT","v":10000,"o":"SALE"},
          {"a":"%s","b":"GUARANTEE","d":"CREDIT","v":8201,"o":"SALE","r":"2026-09-01"},
          {"a":"%s","b":"GUARANTEE","d":"CREDIT","v":1000,"o":"COMMISSION","r":"2026-09-01"},
          {"a":"SYS_PLATFORM_REVENUE","b":"SYSTEM","d":"CREDIT","v":451,"o":"FEE"},
          {"a":"SYS_PROVIDER_FEE","b":"SYSTEM","d":"CREDIT","v":348,"o":"FEE"}]', :V, :A)::jsonb);

SELECT t_ok('SALE credita GUARANTEE do vendedor com 8201', saldo(:V,'GUARANTEE') = 8201);
SELECT t_ok('SALE credita GUARANTEE do afiliado com 1000', saldo(:A,'GUARANTEE') = 1000);
SELECT t_ok('SALE nao credita PENDING (defeito D-01 nao volta)', saldo(:V,'PENDING') = 0);

SELECT t_raises('B1 chave natural recusa a segunda venda da mesma cobranca',
  $$SELECT t_tx('SALE','CHARGE','ffffffff-0000-0000-0000-000000000001','Venda repetida',
      '[{"a":"SYS_CLEARING","b":"SYSTEM","d":"DEBIT","v":10000,"o":"SALE"},
        {"a":"SYS_PLATFORM_REVENUE","b":"SYSTEM","d":"CREDIT","v":10000,"o":"FEE"}]'::jsonb)$$);

SELECT t_raises('RNF-014 transacao que nao soma zero e recusada',
  $$SELECT t_tx('ADJUSTMENT','ADJUSTMENT','x1','desbalanceada',
      '[{"a":"SYS_CLEARING","b":"SYSTEM","d":"DEBIT","v":100,"o":"OTHER"},
        {"a":"SYS_PLATFORM_REVENUE","b":"SYSTEM","d":"CREDIT","v":99,"o":"OTHER"}]'::jsonb)$$);

-- 2. GUARANTEE_RELEASE
SELECT t_tx('GUARANTEE_RELEASE','CHARGE','ffffffff-0000-0000-0000-000000000001','Saida da garantia',
  format('[{"a":"%s","b":"GUARANTEE","d":"DEBIT","v":8201},
          {"a":"%s","b":"PENDING","d":"CREDIT","v":8201,"r":"2026-09-26"},
          {"a":"%s","b":"GUARANTEE","d":"DEBIT","v":1000},
          {"a":"%s","b":"PENDING","d":"CREDIT","v":1000,"r":"2026-09-26"}]', :V,:V,:A,:A)::jsonb);

SELECT t_ok('GUARANTEE_RELEASE esvazia garantia e enche pendente',
  saldo(:V,'GUARANTEE')=0 AND saldo(:V,'PENDING')=8201);

-- 3. RELEASE (reserva 4%)
SELECT t_tx('RELEASE','CHARGE','ffffffff-0000-0000-0000-000000000001','Liberacao D+32',
  format('[{"a":"%s","b":"PENDING","d":"DEBIT","v":8201},
          {"a":"%s","b":"AVAILABLE","d":"CREDIT","v":7873},
          {"a":"%s","b":"RESERVE","d":"CREDIT","v":328,"r":"2026-11-13"},
          {"a":"%s","b":"PENDING","d":"DEBIT","v":1000},
          {"a":"%s","b":"AVAILABLE","d":"CREDIT","v":1000}]', :V,:V,:V,:A,:A)::jsonb);

SELECT t_ok('RELEASE separa disponivel e reserva (4% truncado = 328)',
  saldo(:V,'AVAILABLE')=7873 AND saldo(:V,'RESERVE')=328);
SELECT t_ok('afiliado nao constitui reserva', saldo(:A,'RESERVE')=0 AND saldo(:A,'AVAILABLE')=1000);

-- =====================================================================
-- BLOCO 3 — contestação, dívida, reversão e baixa
-- =====================================================================
\echo ''
\echo '--- BLOCO 3: contestacao, divida, reversao e baixa ---'

-- para chegar ao cenário do documento: disponível de R$ 50,00
SELECT t_tx('PAYOUT','PAYOUT','p-ajuste','Saque de ajuste para o cenario do doc',
  format('[{"a":"%s","b":"AVAILABLE","d":"DEBIT","v":2873},
          {"a":"SYS_CLEARING","b":"SYSTEM","d":"CREDIT","v":2873}]', :V)::jsonb);

INSERT INTO disputes (id,charge_id,kind,amount_cents,acquirer_fee_cents,status)
VALUES ('12121212-0000-0000-0000-000000000001','ffffffff-0000-0000-0000-000000000001',
        'CHARGEBACK',10000,3000,'OPEN');

-- B7: tarifa da adquirente em conta própria
SELECT t_tx('CHARGEBACK','DISPUTE','12121212-0000-0000-0000-000000000001','Contestacao R$ 100 + tarifa R$ 30',
  format('[{"a":"%s","b":"RESERVE","d":"DEBIT","v":328},
          {"a":"%s","b":"AVAILABLE","d":"DEBIT","v":5000},
          {"a":"%s","b":"DEBT","d":"DEBIT","v":6672,"o":"DEBT"},
          {"a":"%s","b":"AVAILABLE","d":"DEBIT","v":1000,"o":"COMMISSION"},
          {"a":"SYS_CLEARING","b":"SYSTEM","d":"CREDIT","v":10000},
          {"a":"SYS_ACQUIRER_FEE","b":"SYSTEM","d":"CREDIT","v":3000,"o":"FEE"}]', :V,:V,:V,:A)::jsonb);

SELECT t_ok('cascata RESERVE -> AVAILABLE -> DEBT',
  saldo(:V,'RESERVE')=0 AND saldo(:V,'AVAILABLE')=0 AND saldo(:V,'DEBT')=-6672);
SELECT t_ok('B7 SYS_CLEARING fecha em zero para esta venda',
  saldo_sys('SYS_CLEARING') = -(10000-2873-10000));
SELECT t_ok('B7 tarifa da adquirente em conta propria', saldo_sys('SYS_ACQUIRER_FEE') = 3000);

-- venda seguinte, com compensação da dívida na saída da garantia
INSERT INTO charges (id,order_id,cycle_number,amount_cents,plan,platform_fee_bps,platform_fee_fixed_cents,
                     platform_fee_cents,affiliate_fee_cents,seller_amount_cents,provider_fee_cents,status,provider_charge_id)
VALUES ('ffffffff-0000-0000-0000-000000000002','eeeeeeee-0000-0000-0000-000000000001',
        1,10000,'TRANSACIONAL',599,200,799,1000,8201,348,'PAID','prov_2');

SELECT t_tx('SALE','CHARGE','ffffffff-0000-0000-0000-000000000002','Venda seguinte',
  format('[{"a":"SYS_CLEARING","b":"SYSTEM","d":"DEBIT","v":10000,"o":"SALE"},
          {"a":"%s","b":"GUARANTEE","d":"CREDIT","v":8201,"o":"SALE"},
          {"a":"%s","b":"GUARANTEE","d":"CREDIT","v":1000,"o":"COMMISSION"},
          {"a":"SYS_PLATFORM_REVENUE","b":"SYSTEM","d":"CREDIT","v":451,"o":"FEE"},
          {"a":"SYS_PROVIDER_FEE","b":"SYSTEM","d":"CREDIT","v":348,"o":"FEE"}]', :V, :A)::jsonb);

SELECT t_tx('GUARANTEE_RELEASE','CHARGE','ffffffff-0000-0000-0000-000000000002','Saida com compensacao',
  format('[{"a":"%s","b":"GUARANTEE","d":"DEBIT","v":8201},
          {"a":"%s","b":"DEBT","d":"CREDIT","v":6672,"o":"DEBT"},
          {"a":"%s","b":"PENDING","d":"CREDIT","v":1529}]', :V,:V,:V)::jsonb);

SELECT t_ok('RF-104 divida quitada na saida da garantia',
  saldo(:V,'DEBT')=0 AND saldo(:V,'PENDING')=1529);

-- =====================================================================
-- BLOCO 4 — os cinco verificadores de integridade
-- =====================================================================
\echo ''
\echo '--- BLOCO 4: verificacoes de integridade ---'

SELECT t_ok('verificacao 1 (soma zero) vazia',      (SELECT count(*)=0 FROM v_check_unbalanced_transactions));
SELECT t_ok('verificacao 2 (bucket de usuario) vazia', (SELECT count(*)=0 FROM v_check_negative_user_buckets));
SELECT t_ok('verificacao 3 (DEBT positivo) vazia',  (SELECT count(*)=0 FROM v_check_positive_debt));
SELECT t_ok('verificacao 4 (sinal de conta de sistema) vazia', (SELECT count(*)=0 FROM v_check_system_sign_violation));
SELECT t_ok('A1 SYS_CLEARING negativo NAO e mais acusado', saldo_sys('SYS_CLEARING') < 0);

SELECT ledger_consolidate_account(:V);
SELECT ledger_consolidate_account(:A);
SELECT t_ok('verificacao 5 (deriva do resumo) vazia', (SELECT count(*)=0 FROM v_check_checkpoint_drift));
SELECT t_ok('resumo de saldo bate com o razao',
  (SELECT balance_cents FROM ledger_checkpoints WHERE account_id=:V AND bucket='PENDING') = saldo(:V,'PENDING'));

-- corrompe de propósito e confere que a verificação 5 acusa
UPDATE ledger_checkpoints SET balance_cents = balance_cents + 1 WHERE account_id=:V AND bucket='PENDING';
SELECT t_ok('verificacao 5 detecta resumo corrompido', (SELECT count(*)>0 FROM v_check_checkpoint_drift));
SELECT ledger_rebuild_checkpoints(:V);
SELECT t_ok('reconstrucao do resumo zera a deriva', (SELECT count(*)=0 FROM v_check_checkpoint_drift));

-- =====================================================================
-- BLOCO 5 — imutabilidade e integridade referencial do razão
-- =====================================================================
\echo ''
\echo '--- BLOCO 5: imutabilidade do razao ---'

SELECT t_raises('razao e append-only (UPDATE)', $$UPDATE ledger_entries SET amount_cents=1 WHERE id=1$$);
SELECT t_raises('razao e append-only (DELETE)', $$DELETE FROM ledger_entries WHERE id=1$$);
SELECT t_raises('transacao do razao e append-only',
  $$UPDATE ledger_transactions SET description='x' WHERE id=(SELECT id FROM ledger_transactions LIMIT 1)$$);
SELECT t_raises('A1 lancamento de usuario exige conta existente',
  $$SELECT t_tx('ADJUSTMENT','ADJUSTMENT','x2','conta inexistente',
      '[{"a":"00000000-dead-0000-0000-000000000000","b":"AVAILABLE","d":"CREDIT","v":10},
        {"a":"SYS_CLEARING","b":"SYSTEM","d":"DEBIT","v":10}]'::jsonb)$$);
SELECT t_raises('A1 bucket SYSTEM exige conta de sistema',
  format($$SELECT t_tx('ADJUSTMENT','ADJUSTMENT','x3','conta de usuario em bucket SYSTEM',
      '[{"a":"%s","b":"SYSTEM","d":"CREDIT","v":10},
        {"a":"SYS_CLEARING","b":"SYSTEM","d":"DEBIT","v":10}]'::jsonb)$$, :V));
SELECT t_raises('valor zero ou negativo recusado',
  $$SELECT t_tx('ADJUSTMENT','ADJUSTMENT','x4','valor zero',
      '[{"a":"SYS_CLEARING","b":"SYSTEM","d":"CREDIT","v":0},
        {"a":"SYS_PLATFORM_REVENUE","b":"SYSTEM","d":"DEBIT","v":0}]'::jsonb)$$);

-- =====================================================================
-- BLOCO 6 — lançamentos que faltavam (payout, taxa, reversão, antecipação)
-- =====================================================================
\echo ''
\echo '--- BLOCO 6: lancamentos completos ---'

-- taxa de verificação em conta zerada: nasce como DEBT, nunca AVAILABLE negativo
INSERT INTO accounts (id,email,password_hash,full_name,person_type,tax_id,kyc_status)
VALUES ('33333333-3333-3333-3333-333333333333','novo@x.com','h','Novo','PF','11111111111','APPROVED');

SELECT t_tx('PLATFORM_FEE','VERIFICATION','33333333-3333-3333-3333-333333333333','Taxa de verificacao',
  '[{"a":"33333333-3333-3333-3333-333333333333","b":"DEBT","d":"DEBIT","v":1200,"o":"FEE"},
    {"a":"SYS_PROVIDER_FEE","b":"SYSTEM","d":"CREDIT","v":900,"o":"FEE"},
    {"a":"SYS_PLATFORM_REVENUE","b":"SYSTEM","d":"CREDIT","v":300,"o":"FEE"}]'::jsonb);

SELECT t_ok('taxa de verificacao vira DEBT, nao AVAILABLE negativo',
  saldo('33333333-3333-3333-3333-333333333333','DEBT') = -1200
  AND saldo('33333333-3333-3333-3333-333333333333','AVAILABLE') = 0);

SELECT t_raises('cobranca dupla da taxa de verificacao impossivel',
  $$SELECT t_tx('PLATFORM_FEE','VERIFICATION','33333333-3333-3333-3333-333333333333','Taxa repetida',
      '[{"a":"33333333-3333-3333-3333-333333333333","b":"DEBT","d":"DEBIT","v":1200,"o":"FEE"},
        {"a":"SYS_PLATFORM_REVENUE","b":"SYSTEM","d":"CREDIT","v":1200,"o":"FEE"}]'::jsonb)$$);

-- payout e reversão
INSERT INTO payouts (id,account_id,amount_cents,bank_account_id,status,idempotency_key)
VALUES ('88888888-0000-0000-0000-000000000001',:V,1000,'99999999-0000-0000-0000-000000000001','REQUESTED','pk-1');

-- credita o afiliado para ter o que sacar
SELECT t_tx('ADJUSTMENT','ADJUSTMENT','seed-afiliado','Credito para o teste de saque',
  format('[{"a":"%s","b":"AVAILABLE","d":"CREDIT","v":1000},
          {"a":"SYS_CLEARING","b":"SYSTEM","d":"DEBIT","v":1000}]', :A)::jsonb);
SELECT t_tx('PAYOUT','PAYOUT','88888888-0000-0000-0000-000000000001','Saque',
  format('[{"a":"%s","b":"AVAILABLE","d":"DEBIT","v":1000},
          {"a":"SYS_CLEARING","b":"SYSTEM","d":"CREDIT","v":1000}]', :A)::jsonb);
SELECT t_tx('PAYOUT_REVERSAL','PAYOUT','88888888-0000-0000-0000-000000000001','Saque recusado',
  format('[{"a":"SYS_CLEARING","b":"SYSTEM","d":"DEBIT","v":1000},
          {"a":"%s","b":"AVAILABLE","d":"CREDIT","v":1000}]', :A)::jsonb);
SELECT t_ok('PAYOUT + PAYOUT_REVERSAL devolvem o saldo exatamente uma vez',
  saldo(:A,'AVAILABLE') = 1000);
SELECT t_raises('reversao de saque nao pode ser aplicada duas vezes',
  format($$SELECT t_tx('PAYOUT_REVERSAL','PAYOUT','88888888-0000-0000-0000-000000000001','Reversao repetida',
      '[{"a":"SYS_CLEARING","b":"SYSTEM","d":"DEBIT","v":1000},
        {"a":"%s","b":"AVAILABLE","d":"CREDIT","v":1000}]'::jsonb)$$, :A));

-- antecipação D+7 sobre 8201
SELECT t_ok('antecipacao: cobrado 187, custo 85, margem 102',
  (8201*229/10000)=187 AND (8201*104/10000)=85 AND (8201*229/10000)-(8201*104/10000)=102);
SELECT t_ok('reserva de 8% sobre 8014 = 641', (8014*800/10000)=641);

-- chargeback revertido
SELECT t_tx('CHARGEBACK_REVERSAL','DISPUTE','12121212-0000-0000-0000-000000000001','Defesa vencida',
  format('[{"a":"SYS_CLEARING","b":"SYSTEM","d":"DEBIT","v":10000},
          {"a":"SYS_CHARGEBACK_LOSS","b":"SYSTEM","d":"DEBIT","v":3000},
          {"a":"%s","b":"AVAILABLE","d":"CREDIT","v":5000},
          {"a":"%s","b":"RESERVE","d":"CREDIT","v":328},
          {"a":"%s","b":"AVAILABLE","d":"CREDIT","v":1000,"o":"COMMISSION"},
          {"a":"%s","b":"PENDING","d":"CREDIT","v":6672}]', :V,:V,:A,:V)::jsonb);
SELECT t_ok('CHARGEBACK_REVERSAL restitui na ordem inversa da cascata',
  saldo(:V,'AVAILABLE')=5000 AND saldo(:V,'RESERVE')=328);

-- =====================================================================
-- BLOCO 7 — correções v3.0: os treze defeitos, agora fechados
-- =====================================================================
\echo ''
\echo '--- BLOCO 7: correcoes v3.0 ---'

-- D04: reembolso de dois ciclos de uma assinatura
INSERT INTO subscriptions (id,order_id,offer_id,status)
VALUES ('7a7a7a7a-0000-0000-0000-000000000001','eeeeeeee-0000-0000-0000-000000000001',
        'bbbbbbbb-0000-0000-0000-000000000001','ACTIVE');
INSERT INTO charges (id,order_id,subscription_id,cycle_number,amount_cents,plan,platform_fee_bps,
                     platform_fee_fixed_cents,platform_fee_cents,affiliate_fee_cents,seller_amount_cents,status)
VALUES ('ffffffff-0000-0000-0000-00000000c003','eeeeeeee-0000-0000-0000-000000000001',
        '7a7a7a7a-0000-0000-0000-000000000001',3,10000,'TRANSACIONAL',599,200,799,1000,8201,'PAID'),
       ('ffffffff-0000-0000-0000-00000000c005','eeeeeeee-0000-0000-0000-000000000001',
        '7a7a7a7a-0000-0000-0000-000000000001',5,10000,'TRANSACIONAL',599,200,799,1000,8201,'PAID');
UPDATE charges SET refunded_cents=10000, status='REFUNDED' WHERE id='ffffffff-0000-0000-0000-00000000c003';
UPDATE charges SET refunded_cents=10000, status='REFUNDED' WHERE id='ffffffff-0000-0000-0000-00000000c005';
SELECT t_ok('D04 reembolso de dois ciclos de uma assinatura e aceito',
  (SELECT count(*)=2 FROM charges WHERE order_id='eeeeeeee-0000-0000-0000-000000000001' AND status='REFUNDED'));
SELECT t_ok('D04 estado consolidado do pedido vem da visao, nao de coluna',
  (SELECT devolvido_cents=20000 FROM v_order_status WHERE order_id='eeeeeeee-0000-0000-0000-000000000001'));

-- D05: parte da parcela maior que a parcela
SELECT t_raises('D05 recebivel com parte maior que o proprio valor recusado',
  $$INSERT INTO receivables (id,charge_id,installment_number,amount_cents,seller_amount_cents,
                             affiliate_amount_cents,expected_at)
    VALUES (gen_random_uuid(),'ffffffff-0000-0000-0000-000000000001',1,5000,9999,9999,now())$$);

-- D06: release_at em debito e em conta de sistema
SELECT t_raises('D06 release_at em lancamento de DEBITO recusado',
  format($$SELECT t_tx('ADJUSTMENT','ADJUSTMENT','d06','debito agendado',
    '[{"a":"%s","b":"AVAILABLE","d":"DEBIT","v":10,"r":"2027-01-01"},
      {"a":"SYS_CLEARING","b":"SYSTEM","d":"CREDIT","v":10}]'::jsonb)$$, :V));

-- D07: agendamento populado pelo banco
SELECT t_ok('D07 todo lancamento com release_at tem linha de agendamento',
  (SELECT count(*)=0 FROM ledger_entries e WHERE e.release_at IS NOT NULL
     AND NOT EXISTS (SELECT 1 FROM ledger_release_schedule s WHERE s.entry_id=e.id)));
SELECT t_ok('D07 agendamento espelha valor, bucket e data do lancamento',
  (SELECT count(*)=0 FROM ledger_entries e JOIN ledger_release_schedule s ON s.entry_id=e.id
     WHERE s.amount_cents<>e.amount_cents OR s.bucket<>e.bucket OR s.release_at<>e.release_at));

-- D08: nao negatividade deixa de ser achado do dia seguinte
SELECT t_raises_def('D08 saque acima do saldo recusado no COMMIT',
  format($$SELECT t_tx('PAYOUT','PAYOUT','d08-a','saque impossivel',
    '[{"a":"%s","b":"AVAILABLE","d":"DEBIT","v":99999999},
      {"a":"SYS_CLEARING","b":"SYSTEM","d":"CREDIT","v":99999999}]'::jsonb)$$, :V));
SELECT t_raises_def('D08 compensacao maior que a divida recusada no COMMIT',
  format($$SELECT t_tx('ADJUSTMENT','ADJUSTMENT','d08-b','compensacao excessiva',
    '[{"a":"%s","b":"DEBT","d":"CREDIT","v":9999,"o":"DEBT"},
      {"a":"SYS_CHARGEBACK_LOSS","b":"SYSTEM","d":"DEBIT","v":9999}]'::jsonb)$$, :V));
SELECT t_ok('D08 nao bloqueia a cascata legitima (DEBT negativo continua valido)',
  saldo(:V,'DEBT') <= 0);

-- D09: saque para conta bancaria de outro titular
SELECT t_raises('D09 saque apontando para conta bancaria de outro titular recusado',
  format($$INSERT INTO payouts (id,account_id,amount_cents,bank_account_id,status,idempotency_key)
    VALUES (gen_random_uuid(),%L,1000,'99999999-0000-0000-0000-000000000001','REQUESTED','pk-cross')$$, :A));
SELECT t_raises('D09 saque para conta bancaria nao verificada recusado',
  format($$INSERT INTO bank_accounts (id,account_id,bank_code,branch,number_enc,number_last4,holder_tax_id)
      VALUES ('99999999-0000-0000-0000-0000000000f2',%L,'001','1','\x00','9','00000000000272');
    INSERT INTO payouts (id,account_id,amount_cents,bank_account_id,status,idempotency_key)
      VALUES (gen_random_uuid(),%L,1000,'99999999-0000-0000-0000-0000000000f2','REQUESTED','pk-nv')$$, :A, :A));

-- D10: disputa de reembolso
SELECT t_raises('D10 disputa com kind=REFUND recusada (reembolso vive em refunds)',
  $$INSERT INTO disputes (id,charge_id,kind,amount_cents,status)
    VALUES (gen_random_uuid(),'ffffffff-0000-0000-0000-000000000002','REFUND',2000,'OPEN')$$);

-- D11: nota em nome de quem nao e o vendedor
SELECT t_raises('D11 NFS-e em nome do afiliado recusada',
  format($$INSERT INTO invoices (id,charge_id,issuer_id,amount_cents)
    VALUES (gen_random_uuid(),'ffffffff-0000-0000-0000-000000000001',%L,10000)$$, :A));
INSERT INTO invoices (id,charge_id,issuer_id,amount_cents)
VALUES (gen_random_uuid(),'ffffffff-0000-0000-0000-000000000001','11111111-1111-1111-1111-111111111111',10000);
SELECT t_ok('D11 NFS-e em nome do vendedor aceita',
  (SELECT count(*)=1 FROM invoices WHERE charge_id='ffffffff-0000-0000-0000-000000000001'));

-- D12: toda conta nasce com plano comercial
SELECT t_ok('D12 nenhuma conta sem plano comercial',
  (SELECT count(*)=0 FROM accounts a
    WHERE NOT EXISTS (SELECT 1 FROM platform_subscriptions p WHERE p.account_id=a.id)));

-- D02: e-mail liberado apos encerramento
DO $$
BEGIN
  UPDATE accounts SET status='CLOSED' WHERE id='33333333-3333-3333-3333-333333333333';
  INSERT INTO accounts (id,email,password_hash,full_name,person_type,tax_id)
  VALUES ('3b3b3b3b-0000-0000-0000-000000000001','novo@x.com','h','Novo de novo','PF','11111111111');
  PERFORM t_ok('D02 e-mail e documento reutilizaveis apos encerramento', true);
EXCEPTION WHEN OTHERS THEN
  PERFORM t_ok('D02 e-mail e documento reutilizaveis apos encerramento', false);
END $$;

-- verificacoes novas
SELECT t_ok('verificacao 6 (cronograma de recebiveis) vazia', (SELECT count(*)=0 FROM v_check_receivable_schedule));
SELECT t_ok('verificacao 8 (agendamento de liberacao) vazia',  (SELECT count(*)=0 FROM v_check_release_schedule));
SELECT t_ok('verificacao 7 detecta acumulador divergente',
  (SELECT count(*)>0 FROM v_check_refund_accumulator));

-- as cinco originais continuam limpas
SELECT ledger_rebuild_checkpoints();
SELECT t_ok('as 5 verificacoes originais continuam vazias',
  (SELECT count(*) FROM v_check_unbalanced_transactions)=0
  AND (SELECT count(*) FROM v_check_negative_user_buckets)=0
  AND (SELECT count(*) FROM v_check_positive_debt)=0
  AND (SELECT count(*) FROM v_check_system_sign_violation)=0
  AND (SELECT count(*) FROM v_check_checkpoint_drift)=0);

\echo ''
\echo '--- FIM ---'
