import { usePollingStatus } from "../lib/usePollingStatus";
import { statusFinalFalhou, statusFinalPago } from "../lib/statusCobranca";
import { Aprovado } from "./Aprovado";
import { Recusado } from "./Recusado";

export function BoletoEmitido({ chargeId, barcode, boletoUrl, dueAt }: {
  chargeId: string;
  barcode: string;
  boletoUrl: string;
  dueAt: string | null;
}) {
  const { status, erro } = usePollingStatus(chargeId, "PENDING");

  if (statusFinalPago(status)) return <Aprovado />;
  if (statusFinalFalhou(status)) return <Recusado />;

  return (
    <div className="success-panel" role="status" aria-live="polite">
      <h2>Boleto emitido</h2>
      <p>Pague até o vencimento para confirmar a compra.</p>
      <textarea readOnly aria-label="Linha digitável do boleto" value={barcode} rows={2} />
      <a className="pay-button" href={boletoUrl} target="_blank" rel="noreferrer">
        Abrir boleto em PDF
      </a>
      {dueAt && <small>Vence em {new Date(dueAt).toLocaleDateString("pt-BR")}.</small>}
      {erro && <small className="field-error" role="alert">{erro}</small>}
    </div>
  );
}
