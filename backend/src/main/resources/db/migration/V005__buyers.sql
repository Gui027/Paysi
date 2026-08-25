CREATE TABLE buyers (
  id             uuid PRIMARY KEY,
  email          citext NOT NULL,
  tax_id         text NOT NULL,
  person_type    text NOT NULL CHECK (person_type IN ('PF','PJ')),
  name           text NOT NULL,
  legal_name     text,                                              -- RF-093
  municipal_reg  text,
  address        jsonb,
  anonymized_at  timestamptz,                                       -- RNF-026
  created_at     timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_buyers_taxid_email ON buyers (tax_id, email) WHERE anonymized_at IS NULL;
CREATE INDEX ON buyers (email);
CREATE INDEX ON buyers (tax_id);       -- monitoramento AM-03
