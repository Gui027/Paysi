"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Skeleton, Toast } from "../../../components/ui";
import { getKyc, KycView, kycStatusLabel, requirementStatusLabel, startKyc } from "../../../lib/kyc";

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
        {polling && <Toast tone="success">Aguardando retorno do provedor de verificação…</Toast>}
        {pollTimedOut && <Toast tone="danger">A verificação ainda está em análise. Atualize esta página em alguns minutos para ver o resultado.</Toast>}
        {kyc.requirements.length === 0 ? <p className="pe-hint">Nenhuma pendência registrada até o momento.</p> : <ul className="kyc-checklist">{kyc.requirements.map(item => {
          const estimated = formatEstimatedAt(item.estimatedAt);
          return <li key={item.code} className="kyc-requirement">
            <div className="ui-labels"><span className={`pe-pill ${pillFor[requirementTone(item.status)]}`}>{requirementStatusLabel(item.status)}</span><strong>{item.label}</strong></div>
            {item.reason && <p>{item.reason}</p>}
            {estimated && <p>Previsão: {estimated}</p>}
          </li>;
        })}</ul>}
        <div className="ui-actions"><button type="button" className="ui-button ui-button-primary" disabled={starting || polling} onClick={() => void handleStart()}>{starting ? "Iniciando…" : kyc.kycStatus === "PENDING" ? "Iniciar verificação" : "Continuar verificação"}</button></div>
      </>}
    </div>
  </section>;
}
