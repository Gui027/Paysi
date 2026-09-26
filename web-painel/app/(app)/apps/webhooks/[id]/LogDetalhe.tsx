"use client";

import { useEffect, useState } from "react";
import { Janela } from "../../../../../components/Janela";
import { ApiRequestError } from "../../../../../lib/api";
import { eventLabel, getLog, LogDetail, logStatusLabel, prettyBody, resendLog } from "../../../../../lib/webhooks";

const dateTimeSeconds = new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "medium" });

export function statusPill(status: keyof typeof logStatusLabel) {
  return status === "SUCCESS" ? "pe-pill-on" : status === "RETRYING" ? "vd-pill-warn" : "af-pill-bad";
}

function Bloco({ titulo, texto }: { titulo: string; texto: string }) {
  const [copied, setCopied] = useState(false);

  async function copy() {
    try {
      await navigator.clipboard.writeText(texto);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  }

  return <section className="wh-block" aria-label={titulo}>
    <header><h3>{titulo}</h3>
      <button type="button" className="wh-copy" aria-label={`Copiar ${titulo.toLowerCase()}`} disabled={!texto} onClick={() => void copy()}>{copied ? "Copiado" : "Copiar"}</button></header>
    <pre tabIndex={0}>{texto}</pre>
  </section>;
}

/** "Detalhes" de um envio: evento, URL, data, status, e a requisição e a resposta completas. */
export function LogDetalhe({ webhookId, eventId, onClose, onResent }: { webhookId: string; eventId: string; onClose: () => void; onResent: () => void }) {
  const [detail, setDetail] = useState<LogDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [resending, setResending] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  async function load() {
    try {
      setDetail(await getLog(webhookId, eventId));
    } catch (loadError) {
      setError(loadError instanceof ApiRequestError ? loadError.message : "Não foi possível carregar os detalhes.");
    }
  }

  useEffect(() => { void load(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [webhookId, eventId]);

  async function resend() {
    setResending(true);
    setNotice(null);
    setError(null);
    try {
      await resendLog(webhookId, eventId);
      setNotice("Webhook reenviado.");
      await load();
      onResent();
    } catch (resendError) {
      setError(resendError instanceof ApiRequestError ? resendError.message : "Não foi possível reenviar o webhook.");
    } finally {
      setResending(false);
    }
  }

  return <Janela wide open title="Detalhes" onClose={onClose}>
    {error && <p className="pe-error" role="alert">{error}</p>}
    {!detail && !error && <p role="status">Carregando…</p>}
    {detail && <div className="wh-detail">
      <p><strong>Evento:</strong> {eventLabel(detail.eventType)}</p>
      <p><strong>Post URL:</strong> {detail.url}</p>
      <p><strong>Data do envio:</strong> {dateTimeSeconds.format(new Date(detail.sentAt))}</p>
      <p><strong>Status da resposta:</strong> <span className={`pe-pill ${statusPill(detail.status)}`}>{logStatusLabel[detail.status]}</span>{detail.statusCode ? ` HTTP ${detail.statusCode}` : ""}{detail.error && !detail.statusCode ? ` (${detail.error})` : ""}</p>
      {detail.attempts > 1 && <p className="pe-hint">{detail.attempts} tentativas de envio.</p>}
      {notice && <p className="vd-msg vd-ok" role="status">{notice}</p>}
      <button type="button" className="ui-button ui-button-secondary" disabled={!detail.canResend || resending} onClick={() => void resend()}>{resending ? "Reenviando…" : "Reenviar webhook"}</button>
      <div className="wh-blocks">
        <Bloco titulo="Requisição" texto={prettyBody(detail.requestBody)} />
        <Bloco titulo="Resposta" texto={prettyBody(detail.responseBody)} />
      </div>
    </div>}
    <div className="ui-actions"><button type="button" className="ui-button ui-button-secondary" onClick={onClose}>Fechar</button></div>
  </Janela>;
}
