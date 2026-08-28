ALTER TABLE ledger_adjustments
  ADD COLUMN reference_id text,
  ADD CONSTRAINT ledger_adjustment_reason_not_blank CHECK (btrim(reason) <> ''),
  ADD CONSTRAINT ledger_adjustment_bucket_valid
    CHECK (bucket IN ('GUARANTEE','AVAILABLE','PENDING','RESERVE','DEBT')),
  ADD CONSTRAINT ledger_adjustment_auto_approval_limit
    CHECK (NOT auto_approved OR amount_cents < 10000);

ALTER TABLE ledger_adjustments DROP CONSTRAINT segregacao_de_funcao;
ALTER TABLE ledger_adjustments ADD CONSTRAINT segregacao_de_funcao CHECK (
  auto_approved
  OR (status IN ('PENDING_APPROVAL','REJECTED') AND approved_by IS NULL)
  OR (status IN ('APPROVED','APPLIED') AND approved_by IS NOT NULL AND approved_by <> requested_by)
);

ALTER TABLE admin_audit_log
  ADD CONSTRAINT admin_audit_reason_not_blank CHECK (btrim(reason) <> '');
