CREATE INDEX idx_ledger_release_due_bucket
  ON ledger_release_schedule (bucket, release_at, entry_id)
  WHERE released_at IS NULL;
