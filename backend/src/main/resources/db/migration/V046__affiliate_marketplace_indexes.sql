CREATE INDEX idx_affiliations_affiliate_created_id
  ON affiliations (affiliate_id, created_at DESC, id DESC);

CREATE INDEX idx_affiliations_product_created_id
  ON affiliations (product_id, created_at DESC, id DESC);

CREATE INDEX idx_products_marketplace_created_id
  ON products (created_at DESC, id DESC)
  WHERE affiliation_enabled = true AND status = 'ACTIVE' AND archived_at IS NULL;
