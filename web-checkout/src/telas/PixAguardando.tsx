import { useState } from "react";
import { qrCodeDataUrl } from "../lib/qrPix";
import { usePollingStatus } from "../lib/usePollingStatus";
import { statusFinalFalhou, statusFinalPago } from "../lib/statusCobranca";
import { Aprovado } from "./Aprovado";
import { Recusado } from "./Recusado";

export function PixAguardando({ chargeId, qrCode, expiresAt }: {
  chargeId: string;
  qrCode: string;
  expiresAt: string | null;
}) {
  const { status, erro } = usePollingStatus(chargeId, "PENDING");
  const [copiado, setCopiado] = useState(false);

  if (statusFinalPago(status)) return <Aprovado />;
  if (statusFinalFalhou(status)) return <Recusado />;

  async function copiar() {
    await navigator.clipboard.writeText(qrCode);
    setCopiado(true);
    window.setTimeout(() => setCopiado(false), 1800);
  }

  return (
    <div className="success-panel" role="status" aria-live="polite">
      <h2>Aguardando pagamento do Pix</h2>
      <p>Abra o app do seu banco, escaneie o QR code ou copie o código abaixo.</p>
      <img alt="QR code do Pix" width={220} height={220} src={qrCodeDataUrl(qrCode)} style={{ imageRendering: "pixelated" }} />
      <textarea readOnly aria-label="Código Pix copia e cola" value={qrCode} rows={3} />
      <button type="button" className="pay-button" onClick={() => void copiar()}>
        {copiado ? "Copiado!" : "Copiar código"}
      </button>
      {expiresAt && <small>Expira em {new Date(expiresAt).toLocaleTimeString("pt-BR")}.</small>}
      {erro && <small className="field-error" role="alert">{erro}</small>}
    </div>
  );
}
