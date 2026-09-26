"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { EmptyState, Skeleton, Toast } from "../../../components/ui";
import { Paginacao } from "../../../components/Paginacao";
import { ApiRequestError } from "../../../lib/api";
import { formatarCentavos } from "../../../lib/moeda";
import { listProducts, Product } from "../../../lib/produtos";
import {
  cycleLabel,
  cycleUnit,
  downloadSubscriptionsCsv,
  emptySubscriptionsQuery,
  listSubscriptions,
  statusText,
  SubscriptionCycle,
  SubscriptionRow,
  SubscriptionsPage,
  SubscriptionsQuery,
  SubscriptionsTab,
  SubscriptionStatus,
  subscriptionStatusLabel,
} from "../../../lib/assinaturas";
import { SaleMethod, saleMethodLabel } from "../../../lib/vendas";
import { AssinaturaDrawer } from "./AssinaturaDrawer";

const dateTime = new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" });
const statusOrder = Object.keys(subscriptionStatusLabel) as SubscriptionStatus[];
const abas: readonly [SubscriptionsTab, string][] = [["active", "Ativas"], ["canceled", "Canceladas"], ["all", "Todas"]];

function pillClass(row: SubscriptionRow) {
  if (row.cancelPending) return "vd-pill-warn";
  return row.status === "ACTIVE" ? "pe-pill-on" : row.status === "PAST_DUE" ? "af-pill-bad" : row.status === "TRIAL" ? "vd-pill-warn" : "";
}

export function AssinaturasPage() {
  const [query, setQuery] = useState<SubscriptionsQuery>(emptySubscriptionsQuery);
  const [search, setSearch] = useState("");
  const [draft, setDraft] = useState<SubscriptionsQuery>(emptySubscriptionsQuery);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [data, setData] = useState<SubscriptionsPage | null>(null);
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);
  const [openId, setOpenId] = useState<string | null>(null);
  const requestId = useRef(0);

  const load = useCallback(async (current: SubscriptionsQuery) => {
    const id = ++requestId.current;
    setLoading(true);
    setError(null);
    try {
      const page = await listSubscriptions(current);
      if (id === requestId.current) setData(page);
    } catch {
      if (id === requestId.current) setError("Não foi possível carregar as assinaturas. Tente novamente.");
    } finally {
      if (id === requestId.current) setLoading(false);
    }
  }, []);

  useEffect(() => { void load(query); }, [load, query]);
  useEffect(() => { listProducts().then(page => setProducts(page.items)).catch(() => undefined); }, []);
  useEffect(() => {
    const timer = window.setTimeout(() => setQuery(current => current.q === search ? current : { ...current, q: search, page: 1 }), 350);
    return () => window.clearTimeout(timer);
  }, [search]);

  function changeTab(tab: SubscriptionsTab) {
    setQuery(current => ({ ...current, tab, page: 1 }));
  }

  function applyFilters() {
    setQuery(current => ({ ...current, statuses: draft.statuses, cycle: draft.cycle, method: draft.method, productId: draft.productId, from: draft.from, to: draft.to, page: 1 }));
    setFiltersOpen(false);
  }

  function clearFilters() {
    setDraft(current => ({ ...current, statuses: [], cycle: "", method: "", productId: "", from: "", to: "" }));
    setQuery(current => ({ ...current, statuses: [], cycle: "", method: "", productId: "", from: "", to: "", page: 1 }));
  }

  function toggleStatus(status: SubscriptionStatus, on: boolean) {
    setDraft(current => ({ ...current, statuses: on ? [...current.statuses, status] : current.statuses.filter(item => item !== status) }));
  }

  async function exportCsv() {
    setExporting(true);
    setNotice(null);
    setError(null);
    try {
      const blob = await downloadSubscriptionsCsv(query);
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `assinaturas-paysi-${new Date().toISOString().slice(0, 10)}.csv`;
      document.body.append(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
      setNotice("Exportação iniciada com sucesso.");
    } catch (exportError) {
      setError(exportError instanceof ApiRequestError ? exportError.message : "Não foi possível exportar as assinaturas.");
    } finally {
      setExporting(false);
    }
  }

  const activeFilters = query.statuses.length + (query.cycle ? 1 : 0) + (query.method ? 1 : 0) + (query.productId ? 1 : 0) + (query.from ? 1 : 0) + (query.to ? 1 : 0);
  const rows = data?.items ?? [];

  return <div className="vd">
    <header className="prod-head">
      <h1>Assinaturas</h1>
      <button type="button" className="ui-button ui-button-secondary" disabled={exporting} onClick={() => void exportCsv()}>{exporting ? "Exportando…" : "Exportar"}</button>
    </header>

    <section className="mk-search" aria-label="Buscar assinaturas">
      <div className="mk-search-bar">
        <label className="mk-search-field"><span className="sr-only">Buscar por cliente, e-mail, produto, CPF ou ID da assinatura</span>
          <input type="search" value={search} onChange={event => setSearch(event.target.value)} placeholder="Buscar por cliente, e-mail, produto, CPF ou ID da assinatura" /></label>
        <button type="button" className="mk-filter-button" aria-expanded={filtersOpen} aria-controls="as-filtros" onClick={() => { setDraft(query); setFiltersOpen(open => !open); }}>Filtros{activeFilters ? ` (${activeFilters})` : ""}</button>
      </div>
      {filtersOpen && <div id="as-filtros" className="mk-filters vd-filters">
        <label className="pe-field"><span>Início de</span><input type="date" value={draft.from} max={draft.to || undefined} onChange={event => setDraft(current => ({ ...current, from: event.target.value }))} /></label>
        <label className="pe-field"><span>Início até</span><input type="date" value={draft.to} min={draft.from || undefined} onChange={event => setDraft(current => ({ ...current, to: event.target.value }))} /></label>
        <label className="pe-field"><span>Produto</span><select value={draft.productId} onChange={event => setDraft(current => ({ ...current, productId: event.target.value }))}><option value="">Todos os produtos</option>{products.map(product => <option key={product.id} value={product.id}>{product.name}</option>)}</select></label>
        <label className="pe-field"><span>Frequência</span><select value={draft.cycle} onChange={event => setDraft(current => ({ ...current, cycle: event.target.value as "" | SubscriptionCycle }))}><option value="">Todas</option>{(Object.keys(cycleLabel) as SubscriptionCycle[]).map(cycle => <option key={cycle} value={cycle}>{cycleLabel[cycle]}</option>)}</select></label>
        <label className="pe-field"><span>Método de pagamento</span><select value={draft.method} onChange={event => setDraft(current => ({ ...current, method: event.target.value as "" | SaleMethod }))}><option value="">Todos</option>{(Object.keys(saleMethodLabel) as SaleMethod[]).map(method => <option key={method} value={method}>{saleMethodLabel[method]}</option>)}</select></label>
        <fieldset className="pe-fieldset vd-status-filter"><legend>Status</legend>
          <div className="vd-status-grid">{statusOrder.map(status => <label className="mk-terms" key={status}><input type="checkbox" checked={draft.statuses.includes(status)} onChange={event => toggleStatus(status, event.target.checked)} /> {subscriptionStatusLabel[status]}</label>)}</div>
        </fieldset>
        <div className="ui-actions vd-filter-actions">
          <button type="button" className="ui-button ui-button-primary" onClick={applyFilters}>Aplicar filtros</button>
          <button type="button" className="ui-button ui-button-secondary" onClick={clearFilters}>Limpar filtros</button>
        </div>
      </div>}
    </section>

    {error && <Toast tone="danger">{error} <button className="toast-action" onClick={() => void load(query)}>Tentar novamente</button></Toast>}
    {notice && <Toast>{notice} <button className="toast-action" onClick={() => setNotice(null)}>Fechar</button></Toast>}

    <div className="vd-summary">
      <section className="dash-card" aria-label="Assinaturas ativas"><span>Assinaturas ativas</span><strong>{data ? data.summary.activeCount : "—"}</strong></section>
      <section className="dash-card" aria-label="Faturamento recorrente mensal"><span>Faturamento recorrente mensal</span><strong>{data ? formatarCentavos(data.summary.monthlyRecurringCents) : "—"}</strong></section>
    </div>

    <section className="prod-panel">
      <div className="pe-tabs vd-tabs" role="tablist" aria-label="Situação das assinaturas">
        {abas.map(([id, label]) => <button key={id} type="button" role="tab" id={`as-aba-${id}`} aria-selected={query.tab === id} aria-controls="as-lista" onClick={() => changeTab(id)}>{label}</button>)}
      </div>
      <div id="as-lista" role="tabpanel" aria-labelledby={`as-aba-${query.tab}`}>
        {loading && !data ? <Skeleton label="Carregando assinaturas" /> : rows.length === 0 ?
          <EmptyState title="Nenhuma assinatura encontrada" description={query.q || activeFilters ? "Ajuste a busca ou os filtros para ver outras assinaturas." : "Quando um cliente assinar um dos seus produtos recorrentes, ele aparece aqui."} /> :
          <div className="dash-table-wrap"><table className="prod-table vd-table" aria-busy={loading}>
            <thead><tr><th scope="col">Data de início</th><th scope="col">Produto</th><th scope="col">Cliente</th><th scope="col">Status</th><th scope="col">Valor líquido</th></tr></thead>
            <tbody>{rows.map(row => <tr key={row.id} className="vd-row" onClick={() => setOpenId(row.id)}>
              <td className="prod-muted">{dateTime.format(new Date(row.createdAt))}</td>
              <td className="prod-muted">{row.productName}{row.offerName ? <small className="vd-offer">{row.offerName}</small> : null}</td>
              <td><button type="button" className="pe-linkbtn vd-buyer" aria-label={`Ver detalhes da assinatura ${row.code} de ${row.buyerName}`} onClick={event => { event.stopPropagation(); setOpenId(row.id); }}><strong>{row.buyerName}</strong><small>{row.buyerEmail}</small></button></td>
              <td><span className={`pe-pill ${pillClass(row)}`}>{statusText(row)}</span></td>
              <td>{row.netCents === null ? <span className="prod-muted">—</span> : `${formatarCentavos(row.netCents)} / ${cycleUnit[row.cycle]}`}</td>
            </tr>)}</tbody>
          </table></div>}
        {data && <Paginacao page={data.page} totalPages={data.totalPages} onChange={page => setQuery(current => ({ ...current, page }))} />}
      </div>
    </section>

    <AssinaturaDrawer subscriptionId={openId} onClose={() => setOpenId(null)} onChanged={() => void load(query)} />
  </div>;
}
