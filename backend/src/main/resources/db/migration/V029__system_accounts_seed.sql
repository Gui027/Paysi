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
