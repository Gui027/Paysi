"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Janela } from "../../../components/Janela";
import { Skeleton, Toast } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/api";
import { parseMoneyToCents } from "../../../lib/ofertas";
import { getKyc, getPendingDocuments, KycView, maskCep, PendingDocument, refreshKycStatus, saveComplianceProfile, startKyc, submitDocument } from "../../../lib/kyc";

export type IdentitySetup = { kyc: KycView; documents: PendingDocument[] | null; documentsError: string | null };

export function needsAsaasDocuments(kyc: KycView) {
  return kyc.kycStatus === "SUBMITTED"
    || kyc.requirements.some(item => item.code === "ASAAS_VERIFICATION" && item.status.toUpperCase() === "PENDING");
}

function externalOnly(document: PendingDocument) {
  const description = (document.description ?? "").toLocaleLowerCase("pt-BR");
  return Boolean(document.externalUrl) || description.includes("link de onboarding") || description.includes("aplicativo");
}

function onboardingUnavailable(documents: PendingDocument[] | null) {
  return Boolean(documents?.length && documents.every(document => externalOnly(document) && !document.externalUrl));
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

function DocumentRow({ document, onSent }: { document: PendingDocument; onSent: () => Promise<void> }) {
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
    </> : <p>O link seguro ainda não foi liberado pela Asaas.</p> : <>
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
  const [error, setError] = useState(Boolean(initialSetup?.documentsError));
  const [refreshing, setRefreshing] = useState(false);
  const [refreshFeedback, setRefreshFeedback] = useState<string | null>(initialSetup?.documentsError ?? null);
  const [modalOpen, setModalOpen] = useState(Boolean(initialSetup && initialSetup.kyc.kycStatus !== "APPROVED"));
  const [working, setWorking] = useState(false);
  const active = useRef(true);
  const openedOnce = useRef(Boolean(initialSetup));
  const retryTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const applyKyc = useCallback((view: KycView) => { if (active.current) { setKyc(view); onStatus?.(view.kycStatus); } }, [onStatus]);
  const loadSetup = useCallback(async (refreshProvider = false) => {
    const kycResult = await Promise.resolve(refreshProvider ? refreshKycStatus() : getKyc()).then(
      value => ({ status: "fulfilled" as const, value }),
      reason => ({ status: "rejected" as const, reason }),
    );
    if (!active.current) return false;
    if (kycResult.status === "rejected") { setError(true); return false; }
    applyKyc(kycResult.value);
    if (!needsAsaasDocuments(kycResult.value)) {
      setDocuments(null);
      setError(false);
      return true;
    }
    const documentsResult = await Promise.resolve(getPendingDocuments()).then(
      value => ({ status: "fulfilled" as const, value }),
      reason => ({ status: "rejected" as const, reason }),
    );
    if (!active.current) return false;
    if (documentsResult.status === "fulfilled") {
      setDocuments(documentsResult.value);
      setError(false);
    } else {
      setDocuments(null);
      setError(true);
    }
    return documentsResult.status === "fulfilled";
  }, [applyKyc]);

  useEffect(() => {
    active.current = true;
    if (!initialSetup) loadSetup().finally(() => { if (active.current) setLoading(false); });
    return () => { active.current = false; if (retryTimer.current) clearTimeout(retryTimer.current); };
  }, [initialSetup, loadSetup]);

  useEffect(() => { if (kyc && kyc.kycStatus !== "APPROVED" && !openedOnce.current) { openedOnce.current = true; setModalOpen(true); } }, [kyc]);
  useEffect(() => { if (kyc?.kycStatus === "APPROVED" && nextParam) router.replace(nextParam); }, [kyc, nextParam, router]);
  useEffect(() => {
    const syncOnReturn = () => { if (kyc?.kycStatus === "SUBMITTED") void loadSetup(true); };
    window.addEventListener("focus", syncOnReturn);
    return () => window.removeEventListener("focus", syncOnReturn);
  }, [kyc?.kycStatus, loadSetup]);

  async function refreshDocuments() {
    if (refreshing) return;
    setRefreshing(true);
    setRefreshFeedback(null);
    setError(false);
    try {
      const nextDocuments = await getPendingDocuments();
      setDocuments(nextDocuments);
      // Carregar os documentos não pode depender da consulta de status: são endpoints
      // distintos da Asaas e o link/upload deve continuar disponível se só o status falhar.
      try {
        applyKyc(await refreshKycStatus());
      } catch {
        // A própria lista já permite concluir a etapa; o status pode ser atualizado depois.
      }
      setRefreshFeedback(nextDocuments.length === 0
        ? "Consulta concluída: não há documentos pendentes."
        : onboardingUnavailable(nextDocuments)
          ? "Consulta concluída: a Asaas ainda não liberou o link."
          : "Documentos atualizados.");
    } catch (refreshError) {
      setError(true);
      setRefreshFeedback(refreshError instanceof ApiRequestError ? refreshError.message : "Não foi possível consultar a Asaas agora.");
    } finally { setRefreshing(false); }
  }
  async function refreshStatus() {
    if (refreshing) return;
    setRefreshing(true);
    setRefreshFeedback(null);
    const updated = await loadSetup(true);
    if (updated) setRefreshFeedback("Status atualizado. A análise ainda está em andamento.");
    setRefreshing(false);
  }
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
  const needsDocuments = needsAsaasDocuments(kyc);
  const awaitingAnalysis = kyc.kycStatus === "SUBMITTED" && documents?.length === 0;
  const waitingForOnboarding = onboardingUnavailable(documents);
  const currentStep = needsProfile ? 1 : approved || awaitingAnalysis ? 3 : 2;

  return <section className="pe-section id-section">
    <div className="pe-section-intro"><h2>Identidade</h2><p>Uma verificação simples para proteger seus recebimentos e saques.</p></div>
    <div className={`pe-card id-overview ${approved ? "is-approved" : ""}`}><span className="id-badge" aria-hidden="true">{approved ? "✓" : "⌁"}</span><div><h3>{approved ? "Identidade verificada" : "Complete sua verificação"}</h3><p>{approved ? "Tudo certo. Sua conta está pronta para vender e sacar." : "Leva poucos minutos e você acompanha cada etapa por aqui."}</p></div>{!approved && <button type="button" className="ui-button ui-button-primary" onClick={() => setModalOpen(true)}>Continuar verificação</button>}</div>

    {!approved && <Janela open={modalOpen} title="Verificação de identidade" wide onClose={() => setModalOpen(false)}>
      <ol className="id-steps" aria-label={`Etapa ${currentStep} de 3`}>{["Seus dados", "Documentos", "Análise"].map((label, index) => <li key={label} className={currentStep > index + 1 ? "is-done" : currentStep === index + 1 ? "is-current" : ""}><span>{currentStep > index + 1 ? "✓" : index + 1}</span><small>{label}</small></li>)}</ol>
      <div className="id-step-panel">
        {error && <Toast tone="danger">{refreshFeedback ?? "Não foi possível consultar a Asaas. Tente novamente."}</Toast>}
        {needsProfile ? <><div className="id-step-heading"><span>1</span><div><h3>Complete seus dados</h3><p>Primeiro, confirme algumas informações.</p></div></div><CompleteProfileForm onSaved={begin} /></> : needsDocuments ? <>
          <div className="id-step-heading"><span>2</span><div><h3>Confirme sua identidade</h3><p>Use o método indicado pela Asaas para concluir com segurança.</p></div></div>
          {documents === null ? <div className="id-analysis"><span aria-hidden="true">◷</span><h3>Preparando seus documentos</h3><p>A Asaas está finalizando a abertura da conta. A primeira consulta pode levar alguns segundos.</p><button type="button" className="ui-button ui-button-secondary" disabled={refreshing} onClick={() => void refreshDocuments()}>{refreshing ? "Atualizando…" : "Atualizar agora"}</button></div> : waitingForOnboarding ? <div className="id-analysis"><span aria-hidden="true">!</span><h3>Link de verificação indisponível</h3><p>A Asaas ainda não liberou o link de identificação para esta conta. Nossa equipe precisa revisar a configuração antes de você continuar.</p>{refreshFeedback && <p className="id-refresh-feedback" role="status">{refreshFeedback}</p>}<button type="button" className="ui-button ui-button-secondary" disabled={refreshing} onClick={() => void refreshDocuments()}>{refreshing ? "Verificando…" : "Verificar liberação"}</button></div> : documents.length > 0 ? <ul className="kyc-checklist">{documents.map(document => <DocumentRow key={document.id} document={document} onSent={refreshDocuments} />)}</ul> : <div className="id-analysis"><span aria-hidden="true">✓</span><h3>Documentos conferidos</h3><p>Não há documentos pendentes. Sua conta está aguardando o retorno da análise.</p>{refreshFeedback && <p className="id-refresh-feedback" role="status">{refreshFeedback}</p>}<button type="button" className="ui-button ui-button-secondary" disabled={refreshing} onClick={() => void refreshStatus()}>{refreshing ? "Verificando…" : "Verificar status"}</button></div>}
        </> : kyc.kycStatus === "SUBMITTED" ? <div className="id-analysis"><span aria-hidden="true">✓</span><h3>Enviado para análise</h3><p>Você não precisa manter esta tela aberta. Avisaremos assim que a verificação for concluída.</p><button type="button" className="ui-button ui-button-secondary" onClick={() => void loadSetup()}>Verificar status</button></div> : <>
          <div className="id-step-heading"><span>1</span><div><h3>Vamos começar?</h3><p>Confira a etapa abaixo e avance quando estiver pronto.</p></div></div>{kyc.requirements.length > 0 && <ul className="id-simple-list">{kyc.requirements.map(item => <li key={item.code}><span aria-hidden="true">○</span>{item.label}</li>)}</ul>}<button type="button" className="ui-button ui-button-primary id-next" disabled={working} onClick={() => void begin()}>{working ? "Preparando…" : "Iniciar verificação"}</button>
        </>}
      </div>
      <p className="id-privacy">🔒 Seus dados são protegidos e usados somente para validar sua identidade.</p>
    </Janela>}
  </section>;
}
