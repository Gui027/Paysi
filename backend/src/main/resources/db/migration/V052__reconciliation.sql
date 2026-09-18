-- BE-14.4: conciliação diária entre o livro-razão e o extrato do provedor.
-- RNF-016: divergência de conciliação superior a R$ 0,01 gera alerta operacional no mesmo dia.

-- Extrato importado do provedor. Não existe API real de extrato hoje, então a
-- importação é um passo explícito (admin), reexecutável: reimportar a mesma
-- referência apenas atualiza o valor, nunca duplica a linha.
CREATE TABLE provider_statement_entries (
  id                 uuid PRIMARY KEY,
  provider_reference text NOT NULL UNIQUE,
  amount_cents       bigint NOT NULL CHECK (amount_cents > 0),
  statement_date     date NOT NULL,
  imported_at        timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON provider_statement_entries (statement_date);

-- Resultado da conciliação, uma linha por (dia, referência do provedor). A
-- chave única é o que torna o job reexecutável: rodar duas vezes para o
-- mesmo dia atualiza a mesma linha em vez de duplicar.
CREATE TABLE reconciliation_entries (
  id                 uuid PRIMARY KEY,
  recon_date         date NOT NULL,
  provider_reference text NOT NULL,
  internal_cents     bigint NOT NULL DEFAULT 0,
  provider_cents     bigint NOT NULL DEFAULT 0,
  difference_cents   bigint NOT NULL,
  status             text NOT NULL CHECK (status IN ('MATCHED','DIVERGED')),
  -- Marca que o alerta já foi disparado para esta linha, para o mesmo job
  -- rodando de novo no mesmo dia não gerar um segundo evento no outbox.
  alerted_at         timestamptz,
  created_at         timestamptz NOT NULL DEFAULT now(),
  updated_at         timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_reconciliation_entries_date_ref
  ON reconciliation_entries (recon_date, provider_reference);
CREATE INDEX ON reconciliation_entries (recon_date) WHERE status = 'DIVERGED';
