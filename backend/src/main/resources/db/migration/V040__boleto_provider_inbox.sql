ALTER TABLE charges
  ADD COLUMN payment_expires_at timestamptz,
  ADD COLUMN boleto_barcode text,
  ADD COLUMN boleto_pdf_url text;
CREATE INDEX ON charges (payment_expires_at)
  WHERE status = 'PENDING' AND payment_expires_at IS NOT NULL;
