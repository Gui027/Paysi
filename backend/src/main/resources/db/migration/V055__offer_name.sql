-- Nome da oferta (ex.: "Plano Pro"), para o vendedor distinguir várias ofertas do mesmo produto.
ALTER TABLE offers
  ADD COLUMN name text CHECK (name IS NULL OR length(btrim(name)) BETWEEN 1 AND 60);
