ALTER TABLE bank_accounts
  ADD COLUMN holder_type text NOT NULL DEFAULT 'PF' CHECK (holder_type IN ('PF','PJ')),
  ADD COLUMN holder_name text NOT NULL DEFAULT '',
  ADD COLUMN account_type text NOT NULL DEFAULT 'CHECKING',
  ADD COLUMN pix_key_type text NOT NULL DEFAULT 'EVP';

ALTER TABLE payouts ADD COLUMN receipt_url text;
