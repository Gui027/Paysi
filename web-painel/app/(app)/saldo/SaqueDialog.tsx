"use client";

import { useEffect, useRef, useState } from "react";
import { MfaConfirmation } from "../../../components/MfaConfirmation";
import { Janela } from "../../../components/Janela";
import { formatarCentavos } from "../../../lib/moeda";
import { financeError, FinanceOverview, formatPixKey } from "../../../lib/financeiro";
import { parsePayoutAmount, requestPayout } from "../../../lib/payout";

/** "Realizar saque": valor, chave Pix de destino e taxa. Acima do limite de segurança pede o código do celular. */
export function SaqueDialog({ open, overview, onClose, onDone, onOpenDados }: { open: boolean; overview: FinanceOverview; onClose: () => void; onDone: () => void; onOpenDados: () => void }) {
  const [amount, setAmount] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [mfaOpen, setMfaOpen] = useState(false);
  const cents = useRef<number | null>(null);
  const idempotency = useRef("");

  useEffect(() => {
    if (open) {
      setAmount("");
      setError(null);
      setMfaOpen(false);
      idempotency.current = globalThis.crypto?.randomUUID?.() ?? `payout-${Date.now()}`;
    }
  }, [open]);

  async function send(challengeId: string | null) {
    if (!overview.pix || cents.current === null) return;
    setSubmitting(true);
    setError(null);
    try {
      await requestPayout(cents.current, overview.pix.bankAccountId, challengeId, idempotency.current);
      setMfaOpen(false);
      onDone();
    } catch (requestError) {
      setError(financeError(requestError, "Não foi possível solicitar o saque. Tente novamente."));
      setMfaOpen(false);
    } finally {
      setSubmitting(false);
    }
  }

  function confirm() {
    const value = parsePayoutAmount(amount);
    if (value === null || value < overview.minPayoutCents) {
      setError(`O saque mínimo é de ${formatarCentavos(overview.minPayoutCents)}.`);
      return;
    }
    if (value > overview.balance.availableCents) {
      setError("O valor é maior que o saldo disponível.");
      return;
    }
    cents.current = value;
    if (value >= overview.mfaThresholdCents) setMfaOpen(true);
    else void send(null);
  }

  return <>
    <Janela open={open && !mfaOpen} title="Realizar saque" onClose={() => !submitting && onClose()}>
      <p className="pe-hint">O valor é enviado por Pix para a chave cadastrada.</p>
      {!overview.pix ? <p className="pe-error" role="alert">Cadastre uma chave Pix antes de sacar. <button type="button" className="pe-linkbtn" onClick={onOpenDados}>Ir para Dados bancários</button></p> : <>
        <label className="pe-field"><span>Valor do saque</span>
          <span className="pe-money"><span aria-hidden="true">R$</span><input inputMode="decimal" placeholder="0,00" aria-label="Valor do saque em reais" value={amount} onChange={event => { setAmount(event.target.value); setError(null); }} /></span>
          <small className="pe-hint">Disponível: {formatarCentavos(overview.balance.availableCents)}</small></label>
        <p className="sq-key"><strong>Chave Pix:</strong> {formatPixKey(overview.pix.key, overview.pix.keyType)}</p>
        <p className="sq-info">{overview.payoutFeeCents > 0 ? `Nós cobramos uma taxa de ${formatarCentavos(overview.payoutFeeCents)} por saque.` : "Saque sem taxa."}</p>
        {error && <p className="pe-error" role="alert">{error}</p>}
        <div className="ui-actions">
          <button type="button" className="ui-button ui-button-secondary" disabled={submitting} onClick={onClose}>Cancelar</button>
          <button type="button" className="ui-button ui-button-primary" disabled={submitting} onClick={confirm}>{submitting ? "Enviando…" : "Confirmar"}</button>
        </div>
      </>}
    </Janela>
    <MfaConfirmation open={open && mfaOpen} operation="PAYOUT" onCancel={() => setMfaOpen(false)} onVerified={challengeId => send(challengeId)} />
  </>;
}
