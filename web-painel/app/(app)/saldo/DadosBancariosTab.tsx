"use client";

import { useEffect, useState } from "react";
import { MfaConfirmation } from "../../../components/MfaConfirmation";
import { Toast } from "../../../components/ui";
import { financeError, FinanceOverview, formatPixKey, savePixKey } from "../../../lib/financeiro";
import { formatDocumento } from "../../../lib/vendas";

function Secao({ titulo, texto, children }: { titulo: string; texto?: React.ReactNode; children: React.ReactNode }) {
  return <section className="pe-section"><div className="pe-section-intro"><h2>{titulo}</h2>{texto && <p>{texto}</p>}</div><div className="pe-card">{children}</div></section>;
}

/** Aba Dados bancários: titular, chave Pix de recebimento e a segurança (duas etapas) que protege essa chave. */
export function DadosBancariosTab({ overview, onChanged, onNeedMfa, onOpenCnpj }: { overview: FinanceOverview; onChanged: () => void; onNeedMfa: () => void; onOpenCnpj: () => void }) {
  const [pixKey, setPixKey] = useState(formatPixKey(overview.pix?.key, overview.pix?.keyType));
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [mfaOpen, setMfaOpen] = useState(false);
  const isCompany = overview.holder.personType === "PJ";
  const documentLabel = isCompany ? "CNPJ" : "CPF";

  useEffect(() => { setPixKey(formatPixKey(overview.pix?.key, overview.pix?.keyType)); }, [overview.pix?.key, overview.pix?.keyType]);

  async function save(challengeId: string | null) {
    setSaving(true);
    setError(null);
    try {
      await savePixKey(pixKey, challengeId);
      setMfaOpen(false);
      setNotice("Chave Pix salva.");
      onChanged();
    } catch (requestError) {
      setError(financeError(requestError, "Não foi possível salvar a chave Pix. Tente novamente."));
      setMfaOpen(false);
    } finally {
      setSaving(false);
    }
  }

  function submit() {
    setNotice(null);
    if (!pixKey.trim()) { setError("Informe a chave Pix."); return; }
    setError(null);
    if (!overview.pix) { void save(null); return; }
    if (!overview.mfaEnabled) { onNeedMfa(); return; }
    setMfaOpen(true);
  }

  return <>
    <Secao titulo="Detalhes" texto={!isCompany && <>Tem CNPJ? <button type="button" className="pe-linkbtn" onClick={onOpenCnpj}>Altere a sua conta para CNPJ</button>.</>}>
      <div className="pe-field"><span>Nome completo</span><span className="db-value">{overview.holder.name}</span></div>
      <div className="pe-field"><span>Documento</span><span className="db-value">{documentLabel} - {formatDocumento(overview.holder.taxId)} {!isCompany && <button type="button" className="pe-linkbtn" onClick={onOpenCnpj}>(Alterar CNPJ)</button>}</span></div>
      <label className="pe-field"><span>País</span><select disabled value="BR" aria-label="País"><option value="BR">Brasil</option></select></label>
    </Secao>

    <Secao titulo="Dados bancários" texto={`Preencha com uma chave Pix da mesma titularidade do seu ${documentLabel}.`}>
      <label className="pe-field"><span>Chave Pix</span><input value={pixKey} autoComplete="off" maxLength={140} aria-invalid={Boolean(error)} onChange={event => { setPixKey(event.target.value); setError(null); setNotice(null); }} /></label>
      <p className="db-info">Evite falhas no saque: a chave Pix deve pertencer ao {documentLabel} ({formatDocumento(overview.holder.taxId)}).</p>
      {error && <p className="pe-error" role="alert">{error}</p>}
      {notice && <p className="vd-ok" role="status">{notice}</p>}
    </Secao>

    <Secao titulo="Segurança" texto="A verificação em duas etapas protege a troca da chave Pix e os saques de valor alto.">
      <p className="db-mfa"><span className={`pe-pill ${overview.mfaEnabled ? "pe-pill-on" : "vd-pill-warn"}`}>{overview.mfaEnabled ? "Ativa" : "Desativada"}</span>
        {!overview.mfaEnabled && <button type="button" className="ui-button ui-button-secondary" onClick={onNeedMfa}>Ativar verificação em duas etapas</button>}</p>
      {!overview.mfaEnabled && overview.pix && <Toast tone="danger">Para trocar a chave Pix, ative antes a verificação em duas etapas.</Toast>}
    </Secao>

    <div className="pe-foot"><span /><button type="button" className="ui-button ui-button-primary" disabled={saving} onClick={submit}>{saving ? "Salvando…" : "Salvar alterações"}</button></div>
    <MfaConfirmation open={mfaOpen} operation="BANK_ACCOUNT_CHANGE" onCancel={() => setMfaOpen(false)} onVerified={challengeId => save(challengeId)} />
  </>;
}
