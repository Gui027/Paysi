import { useState } from "react";
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
      <img
        alt="QR code do Pix"
        aria-hidden={false}
        src={`data:image/svg+xml,${encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="180" height="180"><rect width="180" height="180" fill="#fff"/><text x="50%" y="50%" text-anchor="middle" font-size="10">${qrCode}</text></svg>`)}`}
      />
      <textarea readOnly aria-label="Código Pix copia e cola" value={qrCode} rows={3} />
      <button type="button" className="pay-button" onClick={() => void copiar()}>
        {copiado ? "Copiado!" : "Copiar código"}
      </button>
      {expiresAt && <small>Expira em {new Date(expiresAt).toLocaleTimeString("pt-BR")}.</small>}
      {erro && <small className="field-error" role="alert">{erro}</small>}
    </div>
  );
}
