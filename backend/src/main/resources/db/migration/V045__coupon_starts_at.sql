ALTER TABLE coupons ADD COLUMN starts_at timestamptz;

ALTER TABLE coupons ADD CONSTRAINT coupon_starts_before_expires
  CHECK (starts_at IS NULL OR expires_at IS NULL OR starts_at < expires_at);
