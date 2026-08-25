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
