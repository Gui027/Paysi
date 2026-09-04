"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { getKyc, KycView, kycStatusLabel, requirementStatusLabel, startKyc } from "../../../lib/kyc";
import { Botao, Etiqueta, Skeleton, Toast } from "../../../components/ui";

const POLL_INTERVAL_MS = 4000;
const POLL_MAX_ATTEMPTS = 30; // ~2 minutos — evita polling indefinido enquanto o provedor analisa.

function statusTone(status: KycView["kycStatus"]): "neutral" | "success" | "warning" | "danger" {
  if (status === "APPROVED") return "success";
  if (status === "SUBMITTED") return "warning";
  if (status === "REJECTED") return "danger";
  return "neutral";
}

function requirementTone(status: string): "neutral" | "success" | "warning" | "danger" {
  const normalized = status.toUpperCase();
  if (["APPROVED", "OK", "COMPLETED", "DONE"].includes(normalized)) return "success";
  if (["REJECTED", "FAILED", "INVALID"].includes(normalized)) return "danger";
  if (["PENDING", "IN_REVIEW", "SUBMITTED"].includes(normalized)) return "warning";
  return "neutral";
}

function formatEstimatedAt(value: string | null) {
  if (!value) return null;
  return new Intl.DateTimeFormat("pt-BR", { dateStyle: "long" }).format(new Date(value));
}

export function Verificacao() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const nextParam = searchParams.get("next");

  const [kyc, setKyc] = useState<KycView | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [starting, setStarting] = useState(false);
  const [polling, setPolling] = useState(false);
  const [pollTimedOut, setPollTimedOut] = useState(false);

  const activeRef = useRef(true);
  const pollTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const load = useCallback(async () => {
    try {
      const view = await getKyc();
      if (activeRef.current) setKyc(view);
      return view;
    } catch {
      if (activeRef.current) setError(true);
      return null;
    }
  }, []);

  useEffect(() => {
    activeRef.current = true;
    setLoading(true);
    load().finally(() => { if (activeRef.current) setLoading(false); });
    return () => {
      activeRef.current = false;
      if (pollTimer.current) clearTimeout(pollTimer.current);
    };
  }, [load]);

  const stopPolling = useCallback(() => {
    if (pollTimer.current) clearTimeout(pollTimer.current);
    setPolling(false);
  }, []);

  const pollUntilResolved = useCallback((attempt = 0) => {
    if (!activeRef.current) return;
    if (attempt >= POLL_MAX_ATTEMPTS) {
      setPolling(false);
      setPollTimedOut(true);
      return;
    }
    pollTimer.current = setTimeout(async () => {
      const view = await load();
      if (!activeRef.current) return;
      if (!view || view.kycStatus === "APPROVED" || view.kycStatus === "REJECTED") {
        setPolling(false);
        return;
      }
      pollUntilResolved(attempt + 1);
    }, POLL_INTERVAL_MS);
  }, [load]);

  useEffect(() => {
    if (kyc?.kycStatus === "APPROVED" && nextParam) {
      router.replace(nextParam);
    }
  }, [kyc, nextParam, router]);

  async function handleStart() {
    if (starting) return;
    setStarting(true);
    setError(false);
    setPollTimedOut(false);
    try {
      const view = await startKyc();
      setKyc(view);
      if (view.providerUrl) window.open(view.providerUrl, "_blank", "noopener,noreferrer");
      if (view.kycStatus === "SUBMITTED") {
        setPolling(true);
        pollUntilResolved();
      }
    } catch {
      setError(true);
    } finally {
      setStarting(false);
    }
  }

  if (loading) return <Skeleton label="Carregando verificação de identidade" />;
  if (error || !kyc) return <Toast tone="danger">Não foi possível carregar sua verificação de identidade. <button className="toast-action" onClick={() => { setError(false); setLoading(true); load().finally(() => setLoading(false)); }}>Tentar novamente</button></Toast>;

  const rejectedRequirements = kyc.requirements.filter(item => requirementTone(item.status) === "danger");

  return <>
    <header className="content-header">
      <div>
        <h1>Verificação de identidade</h1>
        <p>Acompanhe o status da verificação exigida para liberar saques e publicações.</p>
      </div>
      <Etiqueta tone={statusTone(kyc.kycStatus)}>{kycStatusLabel[kyc.kycStatus]}</Etiqueta>
    </header>

    {kyc.kycStatus === "REJECTED" && (
      <Toast tone="danger">
        A verificação foi recusada.
        {rejectedRequirements.length > 0 ? " Corrija os itens abaixo e reenvie:" : " Reenvie a verificação para tentar novamente."}
        {rejectedRequirements.length > 0 && (
          <ul>
            {rejectedRequirements.map(item => <li key={item.code}>{item.label}{item.reason ? `: ${item.reason}` : ""}</li>)}
          </ul>
        )}
      </Toast>
    )}

    {kyc.kycStatus === "APPROVED" && <Toast tone="success">Sua identidade foi verificada. {nextParam ? "Redirecionando…" : ""}</Toast>}

    {polling && <Toast tone="success">Aguardando retorno do provedor de verificação…</Toast>}
    {pollTimedOut && <Toast tone="danger">A verificação ainda está em análise. Atualize esta página em alguns minutos para ver o resultado.</Toast>}

    <section className="ui-card" aria-labelledby="kyc-checklist-title">
      <h2 id="kyc-checklist-title">Checklist de verificação</h2>
      {kyc.requirements.length === 0 ? (
        <p>Nenhuma pendência registrada até o momento.</p>
      ) : (
        <ul className="kyc-checklist">
          {kyc.requirements.map(item => {
            const estimatedAt = formatEstimatedAt(item.estimatedAt);
            return <li key={item.code} className="kyc-requirement">
              <div className="ui-labels"><Etiqueta tone={requirementTone(item.status)}>{requirementStatusLabel(item.status)}</Etiqueta><strong>{item.label}</strong></div>
              {item.reason && <p>{item.reason}</p>}
              {estimatedAt && <p>Previsão: {estimatedAt}</p>}
            </li>;
          })}
        </ul>
      )}
    </section>

    {kyc.kycStatus !== "APPROVED" && (
      <div className="ui-actions">
        <Botao variant="primary" disabled={starting || polling} onClick={() => void handleStart()}>
          {starting ? "Iniciando…" : kyc.kycStatus === "PENDING" ? "Iniciar verificação" : "Continuar verificação"}
        </Botao>
      </div>
    )}
  </>;
}
