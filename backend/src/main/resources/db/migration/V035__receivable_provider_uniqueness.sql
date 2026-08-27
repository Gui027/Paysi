CREATE UNIQUE INDEX uq_receivables_provider_id
  ON receivables (provider_receivable_id)
  WHERE provider_receivable_id IS NOT NULL;
