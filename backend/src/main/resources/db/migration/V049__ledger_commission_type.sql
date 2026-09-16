-- BE-09.2: liquidação de comissão de afiliado é seu próprio tipo de transação,
-- separado de SALE (que ainda não credita vendedor/plataforma — feito por outro cartão).
ALTER TABLE ledger_transactions DROP CONSTRAINT ledger_transactions_type_check;
ALTER TABLE ledger_transactions ADD CONSTRAINT ledger_transactions_type_check CHECK (type IN
  ('SALE','COMMISSION','GUARANTEE_RELEASE','RELEASE','RESERVE_RELEASE',
   'REFUND','CHARGEBACK','CHARGEBACK_REVERSAL',
   'PAYOUT','PAYOUT_REVERSAL','PLATFORM_FEE',
   'DEBT_WRITEOFF','ADJUSTMENT','ANTICIPATION'));
