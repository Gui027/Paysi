-- Mais um campo que a Asaas exige para criar a subconta (renda/faturamento), confirmado em produção
-- pela mensagem de erro dela.
ALTER TABLE accounts
  ADD COLUMN income_value_cents bigint CHECK (income_value_cents IS NULL OR income_value_cents >= 0);
