"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { EmptyState, Skeleton, Toast } from "../../../../../components/ui";
import { Paginacao } from "../../../../../components/Paginacao";
import { ApiRequestError } from "../../../../../lib/api";
import { pastPresets, presetRange } from "../../../../../lib/relatorios";
import {
  emptyLogsQuery, eventCatalog, eventLabel, listLogs, listWebhooks, logStatusLabel, LogsPage as LogsData, LogsQuery, resendLog, resendLogs,
  WebhookItem,
} from "../../../../../lib/webhooks";
import { PeriodValue, SeletorPeriodo } from "../../../relatorios/SeletorPeriodo";
import { LogDetalhe, statusPill } from "./LogDetalhe";

const dateTime = new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" });

function message(error: unknown, fallback: string) {
  return error instanceof ApiRequestError ? error.message : fallback;
}

export function LogsPage({ webhookId }: { webhookId: string }) {
  const [period, setPeriod] = useState<PeriodValue>(() => ({ preset: "7d", ...presetRange("7d", new Date()) }));
  const [search, setSearch] = useState("");
  const [term, setTerm] = useState("");
  const [event, setEvent] = useState("");
  const [page, setPage] = useState(1);
  const [webhook, setWebhook] = useState<WebhookItem | null>(null);
  const [data, setData] = useState<LogsData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [selected, setSelected] = useState<string[]>([]);
  const [menuId, setMenuId] = useState<string | null>(null);
  const [openId, setOpenId] = useState<string | null>(null);
  const [resending, setResending] = useState(false);
  const requestId = useRef(0);

  const query: LogsQuery = { ...emptyLogsQuery, q: term, event, from: period.from, to: period.to, page };

  const load = useCallback(async (current: LogsQuery) => {
    const request = ++requestId.current;
    setLoading(true);
    setError(null);
    try {
      const result = await listLogs(webhookId, current);
      if (request === requestId.current) { setData(result); setSelected([]); }
    } catch (loadError) {
      if (request === requestId.current) setError(message(loadError, "Não foi possível carregar os logs."));
    } finally {
      if (request === requestId.current) setLoading(false);
    }
  }, [webhookId]);

  useEffect(() => { void load(query); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [load, term, event, period.from, period.to, page]);
  useEffect(() => { listWebhooks("", "").then(items => setWebhook(items.find(item => item.id === webhookId) ?? null)).catch(() => undefined); }, [webhookId]);
  useEffect(() => { const timer = setTimeout(() => { setTerm(search); setPage(1); }, 300); return () => clearTimeout(timer); }, [search]);

  const rows = data?.items ?? [];
  const allSelected = rows.length > 0 && selected.length === rows.length;

  async function resendOne(eventId: string) {
    setMenuId(null);
    try {
      await resendLog(webhookId, eventId);
      setNotice("Webhook reenviado");
      void load(query);
    } catch (resendError) {
      setError(message(resendError, "Não foi possível reenviar o webhook."));
    }
  }

  async function resendSelected() {
    setResending(true);
    try {
      const result = await resendLogs(webhookId, selected);
      setNotice(result.sent === 1 ? "1 webhook reenviado" : `${result.sent} webhooks reenviados`);
      void load(query);
    } catch (resendError) {
      setError(message(resendError, "Não foi possível reenviar os webhooks."));
    } finally {
      setResending(false);
    }
  }

  const title = webhook ? `${webhook.name ?? "Webhook"} - ${webhook.productName ?? "Todos que sou produtor"}` : "Logs do webhook";

  return <div className="vd">
    <header className="rel-head api-title">
      <div className="rel-title">
        <Link href="/apps/webhooks" className="rel-back" aria-label="Voltar para Webhooks">←</Link>
        <svg width="46" height="46" viewBox="0 0 24 24" fill="none" stroke="#111827" strokeWidth="1.700" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false"><circle cx="12" cy="6" r="2.500" /><circle cx="6" cy="17" r="2.500" /><circle cx="18" cy="17" r="2.500" /><path d="M12 8.500 8 15M9 17h6M13.500 8.500 16.500 14.500" /></svg>
        <h1>{title}</h1>
      </div>
      <button type="button" className="ui-button ui-button-secondary" onClick={() => void load(query)}>Atualizar</button>
    </header>

    {error && <Toast tone="danger">{error} <button className="toast-action" onClick={() => void load(query)}>Tentar novamente</button></Toast>}
    {notice && <Toast>{notice} <button className="toast-action" onClick={() => setNotice(null)}>Fechar</button></Toast>}

    <section className="prod-panel">
      <div className="rel-toolbar">
        <label className="mk-search-field rel-search"><span className="sr-only">Buscar por ID da venda, e-mail ou ID do evento</span>
          <input type="search" value={search} onChange={event => setSearch(event.target.value)} placeholder="Buscar…" /></label>
        <div className="rel-filters">
          <button type="button" className="ui-button ui-button-secondary" disabled={selected.length === 0 || resending} onClick={() => void resendSelected()}>{resending ? "Reenviando…" : "Reenviar webhooks"}</button>
          <label className="rel-select"><span className="sr-only">Evento</span>
            <select value={event} onChange={change => { setEvent(change.target.value); setPage(1); }}>
              <option value="">Todos os eventos</option>
              {eventCatalog.map(item => <option key={item.key} value={item.key}>{item.label}</option>)}
            </select></label>
          <SeletorPeriodo value={period} presets={pastPresets} onChange={value => { setPeriod(value); setPage(1); }} />
        </div>
      </div>

      {loading && !data ? <Skeleton label="Carregando logs" /> : rows.length === 0 ?
        <EmptyState title="Nenhum envio" description={term || event ? "Nenhum envio encontrado com esses filtros." : "Nenhum evento foi enviado para este webhook no período."} /> :
        <div className="dash-table-wrap"><table className="prod-table vd-table" aria-busy={loading}>
          <thead><tr>
            <th scope="col" className="wh-check"><input type="checkbox" aria-label="Selecionar todos os envios" checked={allSelected} onChange={change => setSelected(change.target.checked ? rows.map(row => row.eventId) : [])} /></th>
            <th scope="col">Data</th><th scope="col">Evento</th><th scope="col">ID venda</th><th scope="col">Status</th><th scope="col"><span className="sr-only">Ações</span></th>
          </tr></thead>
          <tbody>{rows.map(row => <tr key={row.eventId}>
            <td className="wh-check"><input type="checkbox" aria-label={`Selecionar envio de ${eventLabel(row.eventType)} em ${dateTime.format(new Date(row.sentAt))}`} checked={selected.includes(row.eventId)}
              onChange={change => setSelected(current => change.target.checked ? [...current, row.eventId] : current.filter(id => id !== row.eventId))} /></td>
            <td className="prod-muted">{dateTime.format(new Date(row.sentAt))}</td>
            <td className="prod-muted">{eventLabel(row.eventType)}</td>
            <td className="prod-muted">{row.saleCode ?? ""}</td>
            <td><span className={`pe-pill ${statusPill(row.status)}`}>{logStatusLabel[row.status]}</span></td>
            <td className="col-actions"><div className="vd-menu">
              <button type="button" className="vd-kebab" aria-label={`Ações do envio de ${eventLabel(row.eventType)} em ${dateTime.format(new Date(row.sentAt))}`} aria-haspopup="menu" aria-expanded={menuId === row.eventId} onClick={() => setMenuId(current => current === row.eventId ? null : row.eventId)}>⋮</button>
              {menuId === row.eventId && <div className="prod-menu-list vd-menu-list" role="menu">
                <button type="button" role="menuitem" className="col-item" onClick={() => void resendOne(row.eventId)}>Reenviar</button>
                <button type="button" role="menuitem" className="col-item" onClick={() => { setMenuId(null); setOpenId(row.eventId); }}>Ver logs</button>
              </div>}
            </div></td>
          </tr>)}</tbody>
        </table></div>}
      {data && <Paginacao page={data.page} totalPages={data.totalPages} onChange={setPage} />}
    </section>

    {openId && <LogDetalhe webhookId={webhookId} eventId={openId} onClose={() => setOpenId(null)} onResent={() => void load(query)} />}
  </div>;
}
