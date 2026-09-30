"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Janela } from "../../../components/Janela";
import { Skeleton, Toast } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/api";
import { parseMoneyToCents } from "../../../lib/ofertas";
import { getKyc, getPendingDocuments, KycView, maskCep, PendingDocument, saveComplianceProfile, startKyc, submitDocument } from "../../../lib/kyc";

export type IdentitySetup = { kyc: KycView; documents: PendingDocument[] };

function externalOnly(document: PendingDocument) {
  const description = (document.description ?? "").toLocaleLowerCase("pt-BR");
  return Boolean(document.externalUrl) || description.includes("link de onboarding") || description.includes("aplicativo");
}

function CompleteProfileForm({ onSaved }: { onSaved: () => Promise<void> }) {
  const [postalCode, setPostalCode] = useState("");
  const [birthDate, setBirthDate] = useState("");
  const [income, setIncome] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    if (saving) return;
    const incomeValueCents = parseMoneyToCents(income);
    if (incomeValueCents === null) { setError("Informe uma renda/faturamento válido."); return; }
    setSaving(true);
    setError(null);
    try {
      await saveComplianceProfile(postalCode, birthDate, incomeValueCents);
      await onSaved();
    } catch (saveError) {
      setError(saveError instanceof ApiRequestError ? saveError.message : "Não foi possível salvar. Confira os dados e tente de novo.");
    } finally { setSaving(false); }
  }

  return <div className="id-complete-profile">
    <p className="id-step-copy">Precisamos destes dados para abrir sua conta de recebimento com segurança.</p>
    <label className="pe-field"><span>CEP</span><input inputMode="numeric" placeholder="00000-000" autoComplete="postal-code" value={postalCode} onChange={event => setPostalCode(maskCep(event.target.value))} /></label>
    <label className="pe-field"><span>Data de nascimento</span><input type="date" autoComplete="bday" value={birthDate} onChange={event => setBirthDate(event.target.value)} /></label>
    <label className="pe-field"><span>Renda/faturamento mensal</span><span className="pe-money"><span aria-hidden="true">R$</span><input inputMode="decimal" placeholder="0,00" aria-label="Renda/faturamento mensal em reais" value={income} onChange={event => setIncome(event.target.value)} /></span></label>
    {error && <p className="pe-error" role="alert">{error}</p>}
    <button type="button" className="ui-button ui-button-primary id-next" disabled={saving} onClick={() => void submit()}>{saving ? "Preparando próxima etapa…" : "Continuar"}</button>
  </div>;
}

function DocumentRow({ document, onSent, onRefresh }: { document: PendingDocument; onSent: () => Promise<void>; onRefresh: () => Promise<void> }) {
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sent, setSent] = useState(false);

  async function handleFile(file: File) {
    setSending(true);
    setError(null);
    try {
      await submitDocument(document.id, file);
      setSent(true);
      await onSent();
    } catch (sendError) {
      setError(sendError instanceof ApiRequestError ? sendError.message : "Não foi possível enviar o documento. Tente de novo.");
    } finally { setSending(false); }
  }

  const label = document.description && !externalOnly(document) ? document.description : "Documento e selfie";
  return <li className="kyc-document"><div className="id-document-icon" aria-hidden="true">▣</div><div className="id-document-body"><strong>{label}</strong>
    {sent ? <p className="id-success">✓ Enviado. Agora é só aguardar a análise.</p> : externalOnly(document) ? document.externalUrl ? <>
      <p>A identificação é concluída em um ambiente seguro da Asaas.</p><a className="ui-button ui-button-primary id-external" href={document.externalUrl} target="_blank" rel="noopener noreferrer">Fazer verificação segura</a>
    </> : <><p>O link seguro ainda está sendo preparado. Isso costuma levar alguns segundos.</p><button type="button" className="ui-button ui-button-secondary" onClick={() => void onRefresh()}>Buscar link novamente</button></> : <>
      <label className="id-upload"><span>{sending ? "Enviando…" : "Selecionar arquivo"}</span><input type="file" accept="image/png,image/jpeg,application/pdf" aria-label={`Enviar ${label}`} onChange={event => { const file = event.target.files?.[0]; if (file) void handleFile(file); event.target.value = ""; }} disabled={sending} /></label>
      <small>PNG, JPG ou PDF de até 10 MB</small>{error && <p className="pe-error" role="alert">{error}</p>}
    </>}
  </div></li>;
}

export function IdentidadeTab({ onStatus, initialSetup }: { onStatus?: (status: KycView["kycStatus"]) => void; initialSetup?: IdentitySetup | null }) {
  const router = useRouter();
  const nextParam = useSearchParams().get("next");
  const [kyc, setKyc] = useState<KycView | null>(initialSetup?.kyc ?? null);
  const [documents, setDocuments] = useState<PendingDocument[] | null>(initialSetup?.documents ?? null);
  const [loading, setLoading] = useState(!initialSetup);
  const [error, setError] = useState(false);
  const [modalOpen, setModalOpen] = useState(Boolean(initialSetup && initialSetup.kyc.kycStatus !== "APPROVED"));
  const [working, setWorking] = useState(false);
  const active = useRef(true);
  const openedOnce = useRef(Boolean(initialSetup));
  const retryTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const linkRetries = useRef(0);

  const applyKyc = useCallback((view: KycView) => { if (active.current) { setKyc(view); onStatus?.(view.kycStatus); } }, [onStatus]);
  const loadSetup = useCallback(async () => {
    const [kycResult, documentsResult] = await Promise.allSettled([getKyc(), getPendingDocuments()]);
    if (!active.current) return;
    if (kycResult.status === "rejected") { setError(true); return; }
    applyKyc(kycResult.value);
    setDocuments(documentsResult.status === "fulfilled" ? documentsResult.value : []);
    setError(false);
  }, [applyKyc]);

  useEffect(() => {
    active.current = true;
    if (!initialSetup) loadSetup().finally(() => { if (active.current) setLoading(false); });
    return () => { active.current = false; if (retryTimer.current) clearTimeout(retryTimer.current); };
  }, [initialSetup, loadSetup]);

  useEffect(() => { if (kyc && kyc.kycStatus !== "APPROVED" && !openedOnce.current) { openedOnce.current = true; setModalOpen(true); } }, [kyc]);
  useEffect(() => { if (kyc?.kycStatus === "APPROVED" && nextParam) router.replace(nextParam); }, [kyc, nextParam, router]);
  useEffect(() => {
    const syncOnReturn = () => { if (kyc?.kycStatus === "SUBMITTED") void loadSetup(); };
    window.addEventListener("focus", syncOnReturn);
    return () => window.removeEventListener("focus", syncOnReturn);
  }, [kyc?.kycStatus, loadSetup]);

  useEffect(() => {
    const waitingForLink = documents?.some(document => externalOnly(document) && !document.externalUrl);
    if (!waitingForLink) { linkRetries.current = 0; return; }
    if (linkRetries.current >= 3) return;
    retryTimer.current = setTimeout(() => {
      linkRetries.current += 1;
      if (active.current) void refreshDocuments();
    }, 5_000);
    return () => { if (retryTimer.current) clearTimeout(retryTimer.current); };
  }, [documents]);

  async function refreshDocuments() { try { setDocuments(await getPendingDocuments()); } catch { setError(true); } }
  async function begin() {
    if (working) return;
    setWorking(true); setError(false);
    try {
      const view = await startKyc();
      applyKyc(view);
      if (view.providerUrl) window.open(view.providerUrl, "_blank", "noopener,noreferrer");
      if (view.requirements.some(item => item.code === "ASAAS_VERIFICATION")) {
        setDocuments(null);
        retryTimer.current = setTimeout(() => { if (active.current) void refreshDocuments(); }, 15_000);
      }
    } catch { setError(true); } finally { setWorking(false); }
  }

  if (loading) return <Skeleton label="Preparando sua verificação" />;
  if (error && !kyc) return <Toast tone="danger">Não foi possível carregar sua verificação. <button className="toast-action" onClick={() => { setError(false); setLoading(true); loadSetup().finally(() => setLoading(false)); }}>Tentar novamente</button></Toast>;
  if (!kyc) return null;

  const approved = kyc.kycStatus === "APPROVED";
  const needsProfile = kyc.requirements.some(item => item.code === "CONTACT_INFO" && item.status.toUpperCase() === "PENDING");
  const needsDocuments = kyc.requirements.some(item => item.code === "ASAAS_VERIFICATION" && item.status.toUpperCase() === "PENDING");
  const currentStep = needsProfile ? 1 : approved ? 3 : 2;

  return <section className="pe-section id-section">
    <div className="pe-section-intro"><h2>Identidade</h2><p>Uma verificação simples para proteger seus recebimentos e saques.</p></div>
    <div className={`pe-card id-overview ${approved ? "is-approved" : ""}`}><span className="id-badge" aria-hidden="true">{approved ? "✓" : "⌁"}</span><div><h3>{approved ? "Identidade verificada" : "Complete sua verificação"}</h3><p>{approved ? "Tudo certo. Sua conta está pronta para vender e sacar." : "Leva poucos minutos e você acompanha cada etapa por aqui."}</p></div>{!approved && <button type="button" className="ui-button ui-button-primary" onClick={() => setModalOpen(true)}>Continuar verificação</button>}</div>

    {!approved && <Janela open={modalOpen} title="Verificação de identidade" wide onClose={() => setModalOpen(false)}>
      <ol className="id-steps" aria-label={`Etapa ${currentStep} de 3`}>{["Seus dados", "Documentos", "Análise"].map((label, index) => <li key={label} className={currentStep > index + 1 ? "is-done" : currentStep === index + 1 ? "is-current" : ""}><span>{currentStep > index + 1 ? "✓" : index + 1}</span><small>{label}</small></li>)}</ol>
      <div className="id-step-panel">
        {error && <Toast tone="danger">Não foi possível atualizar a verificação agora. Tente novamente.</Toast>}
        {needsProfile ? <><div className="id-step-heading"><span>1</span><div><h3>Complete seus dados</h3><p>Primeiro, confirme algumas informações.</p></div></div><CompleteProfileForm onSaved={begin} /></> : needsDocuments ? <>
          <div className="id-step-heading"><span>2</span><div><h3>Confirme sua identidade</h3><p>Use o método indicado pela Asaas para concluir com segurança.</p></div></div>
          {documents === null ? <p className="id-preparing" role="status">Preparando a etapa segura…</p> : documents.length > 0 ? <ul className="kyc-checklist">{documents.map(document => <DocumentRow key={document.id} document={document} onSent={refreshDocuments} onRefresh={refreshDocuments} />)}</ul> : <div className="id-analysis"><span aria-hidden="true">◷</span><h3>Preparando seus documentos</h3><p>A Asaas está finalizando a abertura da conta. O link será atualizado automaticamente.</p><button type="button" className="ui-button ui-button-secondary" onClick={() => void refreshDocuments()}>Atualizar agora</button></div>}
        </> : kyc.kycStatus === "SUBMITTED" ? <div className="id-analysis"><span aria-hidden="true">✓</span><h3>Enviado para análise</h3><p>Você não precisa manter esta tela aberta. Avisaremos assim que a verificação for concluída.</p><button type="button" className="ui-button ui-button-secondary" onClick={() => void loadSetup()}>Verificar status</button></div> : <>
          <div className="id-step-heading"><span>1</span><div><h3>Vamos começar?</h3><p>Confira a etapa abaixo e avance quando estiver pronto.</p></div></div>{kyc.requirements.length > 0 && <ul className="id-simple-list">{kyc.requirements.map(item => <li key={item.code}><span aria-hidden="true">○</span>{item.label}</li>)}</ul>}<button type="button" className="ui-button ui-button-primary id-next" disabled={working} onClick={() => void begin()}>{working ? "Preparando…" : "Iniciar verificação"}</button>
        </>}
      </div>
      <p className="id-privacy">🔒 Seus dados são protegidos e usados somente para validar sua identidade.</p>
    </Janela>}
  </section>;
}
