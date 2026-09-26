"use client";

import Link from "next/link";
import { useCallback, useEffect, useId, useRef, useState } from "react";
import { EmptyState, Skeleton, Toast } from "../../../../components/ui";
import { Paginacao } from "../../../../components/Paginacao";
import { ApiRequestError } from "../../../../lib/api";
import { formatarCentavos } from "../../../../lib/moeda";
import {
  downloadRefundsCsv,
  emptyRefundsQuery,
  formatTelefone,
  listRefunds,
  refundOriginGroups,
  refundOriginLabel,
  RefundRow,
  RefundsPage,
  RefundsQuery,
  refundStatusLabel,
  whatsappUrl,
} from "../../../../lib/vendas";

const dateOnly = new Intl.DateTimeFormat("pt-BR", { dateStyle: "short" });
const statuses = Object.keys(refundStatusLabel) as RefundRow["status"][];

function statusPill(status: RefundRow["status"]) {
  return `pe-pill ${status === "SUCCEEDED" ? "rb-refunded" : status === "FAILED" ? "af-pill-bad" : "vd-pill-warn"}`;
}

function originPill(origin: RefundRow["requestedBy"]) {
  return `rb-origin ${origin === "SELLER" ? "rb-origin-seller" : origin === "BUYER" ? "rb-origin-buyer" : ""}`;
}

function Linha({ rotulo, children }: { rotulo: string; children: React.ReactNode }) {
  return <div><dt>{rotulo}</dt><dd>{children}</dd></div>;
}

/** Painel "Detalhes da solicitação": abas Detalhes e Comprador, com atalho para a venda. */
function SolicitacaoDrawer({ refund, onClose }: { refund: RefundRow | null; onClose: () => void }) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const [tab, setTab] = useState<"detalhes" | "comprador">("detalhes");

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (refund && !dialog.open) { setTab("detalhes"); dialog.showModal(); }
    if (!refund && dialog.open) dialog.close();
  }, [refund]);

  const whatsapp = refund ? whatsappUrl(refund.buyerPhone) : null;

  return <dialog ref={ref} className="mk-drawer" aria-labelledby={titleId} onCancel={event => { event.preventDefault(); onClose(); }}>
    <header className="vd-head"><h2 id={titleId}>Detalhes da solicitação</h2><button type="button" className="cp-close" aria-label="Fechar" onClick={onClose}>✕</button></header>
    {refund && <>
      <div className="pe-tabs mk-tabs" role="tablist" aria-label="Detalhes da solicitação">
        <button type="button" role="tab" id="rb-tab-detalhes" aria-selected={tab === "detalhes"} aria-controls="rb-painel" onClick={() => setTab("detalhes")}>Detalhes</button>
        <button type="button" role="tab" id="rb-tab-comprador" aria-selected={tab === "comprador"} aria-controls="rb-painel" onClick={() => setTab("comprador")}>Comprador</button>
      </div>
      <div id="rb-painel" role="tabpanel" aria-labelledby={`rb-tab-${tab}`} className="mk-drawer-body">
        {tab === "detalhes" ? <dl className="mk-facts">
          <Linha rotulo="Status"><span className={statusPill(refund.status)}>{refundStatusLabel[refund.status]}</span></Linha>
          <Linha rotulo="Solicitação">{dateOnly.format(new Date(refund.createdAt))}</Linha>
          <Linha rotulo="Venda"><Link className="ui-button ui-button-secondary rb-seeall" href={`/vendas?venda=${refund.chargeId}`}>Ver venda {refund.saleCode}</Link></Linha>
          <Linha rotulo="Produto">{refund.productName}</Linha>
          <Linha rotulo="Valor líquido">{formatarCentavos(refund.sellerCents)}</Linha>
          <Linha rotulo="Valor reembolsado">{formatarCentavos(refund.amountCents)}</Linha>
          <Linha rotulo="Autor da requisição">{refundOriginLabel[refund.requestedBy]}</Linha>
          <Linha rotulo="Motivo">{refund.reason || "------"}</Linha>
          {refund.settledAt && <Linha rotulo="Concluído em">{dateOnly.format(new Date(refund.settledAt))}</Linha>}
        </dl> : <dl className="mk-facts">
          <Linha rotulo="Nome">{refund.buyerName}</Linha>
          <Linha rotulo="E-mail">{refund.buyerEmail}</Linha>
          <Linha rotulo="Telefone">{refund.buyerPhone ? <span className="vd-phone">{formatTelefone(refund.buyerPhone)}{whatsapp && <a className="vd-wa" href={whatsapp} target="_blank" rel="noopener noreferrer" aria-label="Conversar no WhatsApp">WhatsApp</a>}</span> : "Não informado"}</Linha>
        </dl>}
      </div>
    </>}
  </dialog>;
}

export function ReembolsosPage() {
  const [query, setQuery] = useState<RefundsQuery>(emptyRefundsQuery);
  const [draft, setDraft] = useState<RefundsQuery>(emptyRefundsQuery);
  const [search, setSearch] = useState("");
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [data, setData] = useState<RefundsPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);
  const [selected, setSelected] = useState<RefundRow | null>(null);
  const requestId = useRef(0);

  const load = useCallback(async (current: RefundsQuery) => {
    const id = ++requestId.current;
    setLoading(true);
    setError(null);
    try {
      const result = await listRefunds(current);
      if (id === requestId.current) setData(result);
    } catch {
      if (id === requestId.current) setError("Não foi possível carregar os reembolsos. Tente novamente.");
    } finally {
      if (id === requestId.current) setLoading(false);
    }
  }, []);

  useEffect(() => { void load(query); }, [load, query]);
  useEffect(() => {
    const timer = window.setTimeout(() => setQuery(current => current.q === search ? current : { ...current, q: search, page: 1 }), 350);
    return () => window.clearTimeout(timer);
  }, [search]);

  function toggle<K extends "statuses" | "origins">(key: K, values: RefundsQuery[K], on: boolean) {
    setDraft(current => {
      const list = current[key] as string[];
      const changed = on ? [...new Set([...list, ...(values as string[])])] : list.filter(item => !(values as string[]).includes(item));
      return { ...current, [key]: changed };
    });
  }

  function applyFilters() {
    setQuery(current => ({ ...current, statuses: draft.statuses, origins: draft.origins, from: draft.from, to: draft.to, page: 1 }));
    setFiltersOpen(false);
  }

  function clearFilters() {
    setDraft(current => ({ ...current, statuses: [], origins: [], from: "", to: "" }));
    setQuery(current => ({ ...current, statuses: [], origins: [], from: "", to: "", page: 1 }));
  }

  async function exportCsv() {
    setExporting(true);
    setNotice(null);
    setError(null);
    try {
      const blob = await downloadRefundsCsv(query);
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `reembolsos-paysi-${new Date().toISOString().slice(0, 10)}.csv`;
      document.body.append(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
      setNotice("Exportação iniciada com sucesso.");
    } catch (exportError) {
      setError(exportError instanceof ApiRequestError ? exportError.message : "Não foi possível exportar os reembolsos.");
    } finally {
      setExporting(false);
    }
  }

  const activeFilters = query.statuses.length + query.origins.length + (query.from ? 1 : 0) + (query.to ? 1 : 0);
  const rows = data?.items ?? [];

  return <div className="vd">
    <header className="prod-head">
      <h1>Reembolsos</h1>
      <button type="button" className="ui-button ui-button-secondary" disabled={exporting} onClick={() => void exportCsv()}>{exporting ? "Exportando…" : "Exportar"}</button>
    </header>

    <section className="mk-search" aria-label="Buscar reembolsos">
      <div className="mk-search-bar">
        <label className="mk-search-field"><span className="sr-only">Buscar por comprador, e-mail, produto ou ID da venda</span>
          <input type="search" value={search} onChange={event => setSearch(event.target.value)} placeholder="Buscar por comprador, e-mail, produto ou ID da venda" /></label>
        <button type="button" className="mk-filter-button" aria-expanded={filtersOpen} aria-controls="rb-filtros" onClick={() => { setDraft(query); setFiltersOpen(open => !open); }}>Filtros{activeFilters ? ` (${activeFilters})` : ""}</button>
      </div>
      {filtersOpen && <div id="rb-filtros" className="mk-filters vd-filters">
        <label className="pe-field"><span>De</span><input type="date" value={draft.from} max={draft.to || undefined} onChange={event => setDraft(current => ({ ...current, from: event.target.value }))} /></label>
        <label className="pe-field"><span>Até</span><input type="date" value={draft.to} min={draft.from || undefined} onChange={event => setDraft(current => ({ ...current, to: event.target.value }))} /></label>
        <fieldset className="pe-fieldset vd-status-filter"><legend>Status</legend>
          <div className="vd-status-grid">{statuses.map(status => <label className="mk-terms" key={status}><input type="checkbox" checked={draft.statuses.includes(status)} onChange={event => toggle("statuses", [status], event.target.checked)} /> {refundStatusLabel[status]}</label>)}</div>
        </fieldset>
        <fieldset className="pe-fieldset vd-status-filter"><legend>Autor da requisição</legend>
          <div className="vd-status-grid">{refundOriginGroups.map(group => <label className="mk-terms" key={group.label}><input type="checkbox" checked={group.origins.every(origin => draft.origins.includes(origin))} onChange={event => toggle("origins", group.origins, event.target.checked)} /> {group.label}</label>)}</div>
        </fieldset>
        <div className="ui-actions vd-filter-actions">
          <button type="button" className="ui-button ui-button-primary" onClick={applyFilters}>Aplicar filtros</button>
          <button type="button" className="ui-button ui-button-secondary" onClick={clearFilters}>Limpar filtros</button>
        </div>
      </div>}
    </section>

    {error && <Toast tone="danger">{error} <button className="toast-action" onClick={() => void load(query)}>Tentar novamente</button></Toast>}
    {notice && <Toast>{notice} <button className="toast-action" onClick={() => setNotice(null)}>Fechar</button></Toast>}

    <section className="prod-panel">
      {loading && !data ? <Skeleton label="Carregando reembolsos" /> : rows.length === 0 ?
        <EmptyState title="Nenhum reembolso" description={query.q || activeFilters ? "Ajuste a busca ou os filtros para ver outros reembolsos." : "Quando uma venda for reembolsada, ela aparece aqui. Para reembolsar, abra a venda em Vendas e use “Reembolsar venda”."} /> :
        <div className="dash-table-wrap"><table className="prod-table vd-table" aria-busy={loading}>
          <thead><tr><th scope="col">Solicitação</th><th scope="col">Comprador</th><th scope="col">Status</th><th scope="col">Autor</th><th scope="col">Valor líquido</th></tr></thead>
          <tbody>{rows.map(row => <tr key={row.id} className="vd-row" onClick={() => setSelected(row)}>
            <td className="prod-muted">{dateOnly.format(new Date(row.createdAt))}</td>
            <td><button type="button" className="pe-linkbtn vd-buyer" aria-label={`Ver solicitação de reembolso de ${row.buyerName}`} onClick={event => { event.stopPropagation(); setSelected(row); }}><strong>{row.buyerName}</strong><small>{row.buyerEmail}</small></button></td>
            <td><span className={statusPill(row.status)}>{refundStatusLabel[row.status]}</span></td>
            <td><span className={originPill(row.requestedBy)}>{refundOriginLabel[row.requestedBy]}</span></td>
            <td>{formatarCentavos(row.sellerCents)}</td>
          </tr>)}</tbody>
        </table></div>}
      {data && <Paginacao page={data.page} totalPages={data.totalPages} onChange={page => setQuery(current => ({ ...current, page }))} />}
    </section>

    <SolicitacaoDrawer refund={selected} onClose={() => setSelected(null)} />
  </div>;
}
