-- BE-06.2: dado do Pix (copia-e-cola) emitido pelo provedor. payment_expires_at (V040)
-- já serve tanto para boleto quanto para pix.
ALTER TABLE charges ADD COLUMN pix_qr_code text;
