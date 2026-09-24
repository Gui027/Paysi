import qrcode from "qrcode-generator";

/** Gera o QR Code do Pix (copia-e-cola) no navegador, como data URL — nada sai do dispositivo. */
export function qrCodeDataUrl(payload: string): string {
  const qr = qrcode(0, "M");
  qr.addData(payload);
  qr.make();
  return qr.createDataURL(5, 4);
}
