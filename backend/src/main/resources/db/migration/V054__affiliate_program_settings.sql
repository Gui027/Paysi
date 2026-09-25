-- Configuração do programa de afiliados por produto: comissão padrão, recorrência,
-- aprovação automática e textos exibidos ao afiliado. Sem linha = padrões do programa
-- (aprovação manual, comissão definida pelo vendedor a cada aprovação).
CREATE TABLE affiliate_program_settings (
  product_id     uuid PRIMARY KEY REFERENCES products(id),
  commission_bps int  NOT NULL DEFAULT 3000 CHECK (commission_bps BETWEEN 0 AND 5000),
  recurrence     text NOT NULL DEFAULT 'FIRST_CHARGE' CHECK (recurrence IN ('FIRST_CHARGE', 'ALL_CYCLES')),
  auto_approve   boolean NOT NULL DEFAULT false,
  support_email  text CHECK (support_email IS NULL OR length(support_email) <= 254),
  description    text CHECK (description IS NULL OR length(description) <= 1000),
  updated_at     timestamptz NOT NULL DEFAULT now()
);
