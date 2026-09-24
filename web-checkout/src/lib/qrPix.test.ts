import assert from "node:assert/strict";
import test from "node:test";
import { qrCodeDataUrl } from "./qrPix.js";

test("gera um QR Code real (imagem) para um copia-e-cola de Pix", () => {
  const pix = "00020101021226820014br.gov.bcb.pix2560pix-h.asaas.com/qr/cobv/bc233f4e-318e-4468-89dc-d5c59e96d15e5204000053039865802BR5905Paysi6008Colatina61082970301762070503***63043A7D";
  const url = qrCodeDataUrl(pix);
  assert.match(url, /^data:image\/gif;base64,/);
  assert.ok(url.length > 200);
});
