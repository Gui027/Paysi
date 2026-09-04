CREATE TABLE assets (
  id           uuid PRIMARY KEY,
  owner_id     uuid NOT NULL REFERENCES accounts(id),
  kind         text NOT NULL CHECK (kind IN ('LOGO','BANNER','SIDE_IMAGE')),
  storage_key  text NOT NULL UNIQUE,
  content_type text NOT NULL CHECK (content_type IN ('image/png','image/jpeg')),
  byte_size    bigint NOT NULL CHECK (byte_size BETWEEN 1 AND 5242880),
  width        int NOT NULL CHECK (width BETWEEN 1 AND 4096),
  height       int NOT NULL CHECK (height BETWEEN 1 AND 4096),
  archived_at  timestamptz,
  created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_assets_owner_active ON assets (owner_id, created_at DESC)
  WHERE archived_at IS NULL;

CREATE TABLE offer_appearance (
  offer_id            uuid PRIMARY KEY REFERENCES offers(id),
  logo_asset_id       uuid REFERENCES assets(id),
  banner_asset_id     uuid REFERENCES assets(id),
  side_image_asset_id uuid REFERENCES assets(id),
  primary_color       text NOT NULL DEFAULT '#2563EB'
    CHECK (primary_color ~ '^#[0-9A-F]{6}$'),
  button_text         text NOT NULL DEFAULT 'Comprar agora'
    CHECK (char_length(button_text) BETWEEN 1 AND 40),
  updated_at          timestamptz NOT NULL DEFAULT now()
);

CREATE FUNCTION appearance_assets_match_owner() RETURNS trigger AS $$
DECLARE seller uuid;
BEGIN
  SELECT p.seller_id INTO seller
    FROM offers o JOIN products p ON p.id = o.product_id
   WHERE o.id = NEW.offer_id;

  IF NEW.logo_asset_id IS NOT NULL AND NOT EXISTS (
       SELECT 1 FROM assets WHERE id = NEW.logo_asset_id AND owner_id = seller
         AND kind = 'LOGO' AND archived_at IS NULL) THEN
    RAISE EXCEPTION 'logoAssetId invalido para a oferta';
  END IF;
  IF NEW.banner_asset_id IS NOT NULL AND NOT EXISTS (
       SELECT 1 FROM assets WHERE id = NEW.banner_asset_id AND owner_id = seller
         AND kind = 'BANNER' AND archived_at IS NULL) THEN
    RAISE EXCEPTION 'bannerAssetId invalido para a oferta';
  END IF;
  IF NEW.side_image_asset_id IS NOT NULL AND NOT EXISTS (
       SELECT 1 FROM assets WHERE id = NEW.side_image_asset_id AND owner_id = seller
         AND kind = 'SIDE_IMAGE' AND archived_at IS NULL) THEN
    RAISE EXCEPTION 'sideImageAssetId invalido para a oferta';
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_appearance_assets_owner
  BEFORE INSERT OR UPDATE ON offer_appearance
  FOR EACH ROW EXECUTE FUNCTION appearance_assets_match_owner();
