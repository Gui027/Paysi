-- BE-03.2: complete the draft offer contract without rewriting the historical V003.
ALTER TABLE offers
  ADD COLUMN payout_delay text NOT NULL DEFAULT 'D32'
    CHECK (payout_delay IN ('D32','D15','D7','D2')),
  ADD COLUMN status text NOT NULL DEFAULT 'DRAFT'
    CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED')),
  ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now();

CREATE INDEX idx_offers_product_created_active
  ON offers (product_id, created_at DESC, id DESC)
  WHERE archived_at IS NULL;
