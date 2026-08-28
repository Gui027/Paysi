ALTER TABLE charges
  ADD COLUMN provider_status text
    CHECK (provider_status IN ('APPROVED','DECLINED','PENDING','EXPIRED','ERROR')),
  ADD COLUMN three_ds_eci text,
  ADD COLUMN three_ds_challenge_url text,
  ADD COLUMN pix_fallback_expires_at timestamptz;

ALTER TABLE sale_evidence ADD COLUMN three_ds_eci text;
