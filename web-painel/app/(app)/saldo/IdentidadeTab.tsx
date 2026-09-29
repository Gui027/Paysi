"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Skeleton, Toast } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/api";
import { parseMoneyToCents } from "../../../lib/ofertas";
import { getKyc, getPendingDocuments, KycView, kycStatusLabel, maskCep, PendingDocument, requirementStatusLabel, saveComplianceProfile, startKyc, submitDocument } from "../../../lib/kyc";

const POLL_INTERVAL_MS = 4000;
const POLL_MAX_ATTEMPTS = 30; // ~2 minutos: evita consultar para sempre enquanto o provedor analisa.

function requirementTone(status: string): "neutral" | "success" | "warning" | "danger" {
  const normalized = status.toUpperCase();
  if (["APPROVED", "OK", "COMPLETED", "DONE"].includes(normalized)) return "success";
  if (["REJECTED", "FAILED", "INVALID"].includes(normalized)) return "danger";
  if (["PENDING", "IN_REVIEW", "SUBMITTED"].includes(normalized)) return "warning";
  return "neutral";
}

const pillFor = { neutral: "", success: "pe-pill-on", warning: "vd-pill-warn", danger: "af-pill-bad" } as const;

function formatEstimatedAt(value: string | null) {
  return value ? new Intl.DateTimeFormat("pt-BR", { dateStyle: "long" }).format(new Date(value)) : null;
}

/** CEP, data de nascimento e renda/faturamento: a Asaas exige os três pra criar a subconta que recebe o repasse do vendedor. */
function CompleteProfileForm({ onSaved }: { onSaved: () => void }) {
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
      onSaved();
    } catch (saveError) {
      setError(saveError instanceof ApiRequestError ? saveError.message : "Não foi possível salvar. Confira os dados e tente de novo.");
    } finally {
      setSaving(false);
    }
  }

  return <div className="id-complete-profile">
    <label className="pe-field"><span>CEP</span>
      <input inputMode="numeric" placeholder="00000-000" autoComplete="postal-code" value={postalCode}
        onChange={event => setPostalCode(maskCep(event.target.value))} /></label>
    <label className="pe-field"><span>Data de nascimento</span>
      <input type="date" autoComplete="bday" value={birthDate} onChange={event => setBirthDate(event.target.value)} /></label>
    <label className="pe-field"><span>Renda/faturamento mensal</span>
      <span className="pe-money"><span aria-hidden="true">R$</span><input inputMode="decimal" placeholder="0,00" aria-label="Renda/faturamento mensal em reais" value={income} onChange={event => setIncome(event.target.value)} /></span></label>
    {error && <p className="pe-error" role="alert">{error}</p>}
    <div className="ui-actions">
      <button type="button" className="ui-button ui-button-primary" disabled={saving} onClick={() => void submit()}>{saving ? "Salvando…" : "Salvar e continuar"}</button>
    </div>
  </div>;
}

/** Uma pendência de documento: upload direto pelo painel da Paysi (sem sair pra outro site). */
function DocumentRow({ document, onSent }: { document: PendingDocument; onSent: () => void }) {
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sent, setSent] = useState(false);
  const inputRef = useRef<HTMLInputElement | null>(null);

  async function handleFile(file: File) {
    setSending(true);
    setError(null);
    try {
      await submitDocument(document.id, file);
      setSent(true);
      onSent();
    } catch (sendError) {
      setError(sendError instanceof ApiRequestError ? sendError.message : "Não foi possível enviar o documento. Tente de novo.");
    } finally {
      setSending(false);
    }
  }

  return <li className="kyc-document">
    <div className="ui-labels"><strong>{document.description ?? document.type}</strong></div>
    {sent ? <p className="pe-hint">Documento enviado — aguardando análise.</p> : document.externalUrl ? (
      <p>Este documento precisa ser enviado por um link específico da Asaas: <a href={document.externalUrl} target="_blank" rel="noopener noreferrer">enviar documento</a></p>
    ) : <>
      <input ref={inputRef} type="file" accept="image/png,image/jpeg,application/pdf"
        aria-label={`Enviar ${document.description ?? document.type}`}
        onChange={event => { const file = event.target.files?.[0]; if (file) void handleFile(file); }} disabled={sending} />
      {error && <p className="pe-error" role="alert">{error}</p>}
    </>}
  </li>;
}

/** Documentos pendentes na subconta da Asaas — 100% enviados pelo painel da Paysi, sem redirecionar o vendedor. */
function DocumentsUploadSection() {
  const [documents, setDocuments] = useState<PendingDocument[] | null>(null);
  const [error, setError] = useState(false);

  const load = useCallback(async () => {
    try {
      setDocuments(await getPendingDocuments());
    } catch {
      setError(true);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  if (error) return <Toast tone="danger">Não foi possível carregar os documentos pendentes.</Toast>;
  if (documents === null) return <Skeleton label="Carregando documentos pendentes" />;
  if (documents.length === 0) return <p className="pe-hint">Nenhum documento pendente no momento. Se você acabou de iniciar a verificação, atualize esta página em instantes.</p>;

  return <ul className="kyc-checklist">{documents.map(document => <DocumentRow key={document.id} document={document} onSent={() => void load()} />)}</ul>;
}

/** Aba Identidade: mostra "Identidade verificada" ou leva o vendedor pelo passo a passo da verificação (KYC). */
export function IdentidadeTab({ onStatus }: { onStatus?: (status: KycView["kycStatus"]) => void }) {
  const router = useRouter();
  const nextParam = useSearchParams().get("next");
  const [kyc, setKyc] = useState<KycView | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [starting, setStarting] = useState(false);
  const [polling, setPolling] = useState(false);
  const [pollTimedOut, setPollTimedOut] = useState(false);
  const active = useRef(true);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const load = useCallback(async () => {
    try {
      const view = await getKyc();
      if (active.current) { setKyc(view); onStatus?.(view.kycStatus); }
      return view;
    } catch {
      if (active.current) setError(true);
      return null;
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    active.current = true;
    setLoading(true);
    load().finally(() => { if (active.current) setLoading(false); });
    return () => { active.current = false; if (timer.current) clearTimeout(timer.current); };
  }, [load]);

  const pollUntilResolved = useCallback((attempt = 0) => {
    if (!active.current) return;
    if (attempt >= POLL_MAX_ATTEMPTS) { setPolling(false); setPollTimedOut(true); return; }
    timer.current = setTimeout(async () => {
      const view = await load();
      if (!active.current) return;
      if (!view || view.kycStatus === "APPROVED" || view.kycStatus === "REJECTED") { setPolling(false); return; }
      pollUntilResolved(attempt + 1);
    }, POLL_INTERVAL_MS);
  }, [load]);

  // Quem veio de outra tela (?next=) volta para lá assim que a identidade é aprovada.
  useEffect(() => { if (kyc?.kycStatus === "APPROVED" && nextParam) router.replace(nextParam); }, [kyc, nextParam, router]);

  async function handleStart() {
    if (starting) return;
    setStarting(true);
    setError(false);
    setPollTimedOut(false);
    try {
      const view = await startKyc();
      setKyc(view);
      onStatus?.(view.kycStatus);
      if (view.providerUrl) window.open(view.providerUrl, "_blank", "noopener,noreferrer");
      if (view.kycStatus === "SUBMITTED") { setPolling(true); pollUntilResolved(); }
    } catch {
      setError(true);
    } finally {
      setStarting(false);
    }
  }

  if (loading) return <Skeleton label="Carregando verificação de identidade" />;
  if (error || !kyc) return <Toast tone="danger">Não foi possível carregar sua verificação de identidade. <button className="toast-action" onClick={() => { setError(false); setLoading(true); load().finally(() => setLoading(false)); }}>Tentar novamente</button></Toast>;

  const rejected = kyc.requirements.filter(item => requirementTone(item.status) === "danger");
  const approved = kyc.kycStatus === "APPROVED";
  const needsProfile = kyc.requirements.some(item => item.code === "CONTACT_INFO" && item.status.toUpperCase() === "PENDING");
  const needsDocuments = kyc.requirements.some(item => item.code === "ASAAS_VERIFICATION" && item.status.toUpperCase() === "PENDING");

  return <section className="pe-section">
    <div className="pe-section-intro"><h2>Verifique a sua identidade</h2><p>A verificação é exigida para publicar ofertas e sacar o seu saldo.</p></div>
    <div className="pe-card id-card">
      {approved ? <div className="id-done" role="status">
        <span className="id-badge" aria-hidden="true">✓</span>
        <h3>Identidade verificada</h3>
        <p>Você já pode vender!</p>
      </div> : <>
        <div className="id-head"><h3>{kyc.kycStatus === "REJECTED" ? "Verificação recusada" : kyc.kycStatus === "SUBMITTED" ? "Verificação em análise" : "Falta verificar a sua identidade"}</h3>
          <span className={`pe-pill ${kyc.kycStatus === "REJECTED" ? "af-pill-bad" : "vd-pill-warn"}`}>{kycStatusLabel[kyc.kycStatus]}</span></div>
        {kyc.kycStatus === "REJECTED" && <Toast tone="danger">A verificação foi recusada.{rejected.length > 0 ? " Corrija os itens abaixo e reenvie:" : " Reenvie para tentar novamente."}{rejected.length > 0 && <ul>{rejected.map(item => <li key={item.code}>{item.label}{item.reason ? `: ${item.reason}` : ""}</li>)}</ul>}</Toast>}
        {starting && <Skeleton label="Iniciando verificação…" />}
        {polling && <Skeleton label="Aguardando retorno do provedor de verificação…" />}
        {pollTimedOut && <Toast tone="danger">A verificação ainda está em análise. Atualize esta página em alguns minutos para ver o resultado.</Toast>}
        {kyc.requirements.length === 0 ? <p className="pe-hint">Nenhuma pendência registrada até o momento.</p> : <ul className="kyc-checklist">{kyc.requirements.map(item => {
          const estimated = formatEstimatedAt(item.estimatedAt);
          return <li key={item.code} className="kyc-requirement">
            <div className="ui-labels"><span className={`pe-pill ${pillFor[requirementTone(item.status)]}`}>{requirementStatusLabel(item.status)}</span><strong>{item.label}</strong></div>
            {item.reason && <p>{item.reason}</p>}
            {estimated && <p>Previsão: {estimated}</p>}
          </li>;
        })}</ul>}
        {needsProfile ? <CompleteProfileForm onSaved={() => { setLoading(true); load().finally(() => setLoading(false)); }} /> :
          needsDocuments ? <DocumentsUploadSection /> :
          <div className="ui-actions"><button type="button" className="ui-button ui-button-primary" disabled={starting || polling} onClick={() => void handleStart()}>{starting ? "Iniciando…" : kyc.kycStatus === "PENDING" ? "Iniciar verificação" : "Continuar verificação"}</button></div>}
      </>}
    </div>
  </section>;
}
