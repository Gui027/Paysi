-- BE-03.1: affiliation is a property of the product. Existing offers may
-- disagree because V003 stored the flag per offer; enabling the product when
-- any historical offer was enabled preserves the least restrictive intent.
ALTER TABLE products
  ADD COLUMN affiliation_enabled boolean NOT NULL DEFAULT false;

UPDATE products p
   SET affiliation_enabled = true
 WHERE EXISTS (
       SELECT 1
         FROM offers o
        WHERE o.product_id = p.id
          AND o.affiliates_enabled = true
 );

ALTER TABLE offers
  DROP COLUMN affiliates_enabled;

ALTER TABLE products
  ADD CONSTRAINT products_name_length CHECK (
    char_length(btrim(name)) BETWEEN 1 AND 120
  ),
  ADD CONSTRAINT products_description_length CHECK (
    description IS NULL OR char_length(btrim(description)) BETWEEN 1 AND 2000
  );

DROP INDEX IF EXISTS products_seller_id_idx;

CREATE INDEX idx_products_seller_created_id_active
  ON products (seller_id, created_at DESC, id DESC)
  WHERE archived_at IS NULL;
