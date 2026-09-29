-- Chave de API própria da subconta na Asaas (POST /v3/accounts devolve isso uma única vez — não dá
-- pra recuperar depois). É com ela que a Paysi consulta e envia os documentos de verificação da
-- subconta (GET/POST /v3/myAccount/documents), sem nunca mandar o vendedor pro painel da Asaas.
ALTER TABLE accounts ADD COLUMN provider_access_token_enc bytea;
