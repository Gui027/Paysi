-- Dados exigidos pela Asaas para criar a subconta do vendedor/afiliado (POST /v3/accounts):
-- além de nome, e-mail e documento (já existentes), ela exige CEP e data de nascimento.
-- Nulos até o vendedor preencher na aba Identidade.
ALTER TABLE accounts
  ADD COLUMN postal_code text CHECK (postal_code IS NULL OR postal_code ~ '^[0-9]{8}$'),
  ADD COLUMN birth_date  date CHECK (birth_date IS NULL OR birth_date < CURRENT_DATE);
