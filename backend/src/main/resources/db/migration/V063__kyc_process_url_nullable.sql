-- A Asaas (subconta white-label) não tem nenhuma URL externa de verificação para mandar o vendedor:
-- provider_url deixa de ser obrigatório para caber esse caso.
ALTER TABLE kyc_processes ALTER COLUMN provider_url DROP NOT NULL;
