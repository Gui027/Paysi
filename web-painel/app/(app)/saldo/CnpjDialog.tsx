"use client";

import { useEffect, useState } from "react";
import { MfaConfirmation } from "../../../components/MfaConfirmation";
import { Janela } from "../../../components/Janela";
import { convertToCompany, financeError, FinanceOverview, maskCnpj } from "../../../lib/financeiro";
import { formatDocumento } from "../../../lib/vendas";

/** "Alterar minha conta para CNPJ": irreversível, então confirma com o código de segurança antes de aplicar. */
export function CnpjDialog({ open, overview, onClose, onNeedMfa, onDone }: { open: boolean; overview: FinanceOverview; onClose: () => void; onNeedMfa: () => void; onDone: () => void }) {
  const [legalName, setLegalName] = useState("");
  const [cnpj, setCnpj] = useState("");
  const [pixKey, setPixKey] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [mfaOpen, setMfaOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => { if (open) { setLegalName(""); setCnpj(""); setPixKey(""); setError(null); setMfaOpen(false); } }, [open]);

  function next() {
    if (legalName.trim().length < 3) { setError("Informe a razão social."); return; }
    if (cnpj.replace(/\D/g, "").length !== 14) { setError("Informe o CNPJ completo."); return; }
    if (!pixKey.trim()) { setError("Informe a chave Pix da empresa."); return; }
    setError(null);
    if (!overview.mfaEnabled) { onNeedMfa(); return; }
    setMfaOpen(true);
  }

  async function apply(challengeId: string) {
    setSubmitting(true);
    setError(null);
    try {
      await convertToCompany({ legalName, cnpj, pixKey }, challengeId);
      setMfaOpen(false);
      onDone();
    } catch (requestError) {
      setError(financeError(requestError, "Não foi possível alterar a conta. Tente novamente."));
      setMfaOpen(false);
    } finally {
      setSubmitting(false);
    }
  }

  return <>
    <Janela wide open={open && !mfaOpen} title="Alterar minha conta para CNPJ" onClose={() => !submitting && onClose()}>
      <p className="pe-hint">Suas próximas vendas e saques passam a ser da empresa, e a identidade da empresa precisa ser verificada de novo. Essa ação é irreversível.</p>
      <dl className="mk-facts">
        <div><dt>Nome</dt><dd>{overview.holder.name}</dd></div>
        <div><dt>CPF</dt><dd>{formatDocumento(overview.holder.taxId)}</dd></div>
      </dl>
      <label className="pe-field"><span>Razão Social</span><input value={legalName} maxLength={120} onChange={event => setLegalName(event.target.value)} /></label>
      <label className="pe-field"><span>CNPJ</span><input inputMode="numeric" value={cnpj} onChange={event => setCnpj(maskCnpj(event.target.value))} /></label>
      <label className="pe-field"><span>Chave Pix</span><input value={pixKey} autoComplete="off" onChange={event => setPixKey(event.target.value)} /><small className="pe-hint">Use uma chave da empresa: o próprio CNPJ, e-mail, celular ou chave aleatória.</small></label>
      {error && <p className="pe-error" role="alert">{error}</p>}
      <div className="ui-actions">
        <button type="button" className="ui-button ui-button-secondary" disabled={submitting} onClick={onClose}>Cancelar</button>
        <button type="button" className="ui-button ui-button-primary" disabled={submitting} onClick={next}>Continuar</button>
      </div>
    </Janela>
    <MfaConfirmation open={open && mfaOpen} operation="BANK_ACCOUNT_CHANGE" onCancel={() => setMfaOpen(false)} onVerified={challengeId => apply(challengeId)} />
  </>;
}
