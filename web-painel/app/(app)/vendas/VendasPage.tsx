"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { EmptyState, Skeleton, Toast } from "../../../components/ui";
import { Paginacao } from "../../../components/Paginacao";
import { ApiRequestError } from "../../../lib/api";
import { formatarCentavos } from "../../../lib/moeda";
import { listProducts, Product } from "../../../lib/produtos";
import {
  downloadSalesCsv,
  emptySalesQuery,
  listSales,
  SaleMethod,
  SaleRow,
  saleMethodLabel,
  SalesPage,
  SalesQuery,
  SaleStatus,
  saleStatusLabel,
  saleStatusTone,
} from "../../../lib/vendas";
import { VendaDrawer } from "./VendaDrawer";

const dateTime = new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" });
const statusOrder = Object.keys(saleStatusLabel) as SaleStatus[];

function pillClass(status: SaleStatus) {
  const tone = saleStatusTone[status];
  return `pe-pill ${tone === "success" ? "pe-pill-on" : tone === "warning" ? "vd-pill-warn" : tone === "danger" ? "af-pill-bad" : ""}`;
}

export function VendasPage() {
  const [query, setQuery] = useState<SalesQuery>(emptySalesQuery);
  const [search, setSearch] = useState("");
  const [draft, setDraft] = useState<SalesQuery>(emptySalesQuery);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [data, setData] = useState<SalesPage | null>(null);
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [openId, setOpenId] = useState<string | null>(null);
  const requestId = useRef(0);

  const load = useCallback(async (current: SalesQuery) => {
    const id = ++requestId.current;
    setLoading(true);
    setError(null);
    try {
      const page = await listSales(current);
      if (id === requestId.current) setData(page);
    } catch {
      if (id === requestId.current) setError("Não foi possível carregar as vendas. Tente novamente.");
    } finally {
      if (id === requestId.current) setLoading(false);
    }
  }, []);

  useEffect(() => { void load(query); }, [load, query]);
  useEffect(() => { listProducts().then(page => setProducts(page.items)).catch(() => undefined); }, []);

  // A busca por texto espera um instante depois da digitação para não consultar a cada tecla.
  useEffect(() => {
    const timer = window.setTimeout(() => setQuery(current => current.q === search ? current : { ...current, q: search, page: 1 }), 350);
    return () => window.clearTimeout(timer);
  }, [search]);

  function changeTab(tab: SalesQuery["tab"]) {
    setQuery(current => ({ ...current, tab, page: 1 }));
    setDraft(current => ({ ...current, tab }));
  }

  function applyFilters() {
    setQuery(current => ({ ...current, statuses: draft.statuses, method: draft.method, productId: draft.productId, from: draft.from, to: draft.to, page: 1 }));
    setFiltersOpen(false);
  }

  function clearFilters() {
    const cleared = { ...draft, statuses: [], method: "" as const, productId: "", from: "", to: "" };
    setDraft(cleared);
    setQuery(current => ({ ...current, statuses: [], method: "", productId: "", from: "", to: "", page: 1 }));
  }

  function toggleStatus(status: SaleStatus, on: boolean) {
    setDraft(current => ({ ...current, statuses: on ? [...current.statuses, status] : current.statuses.filter(item => item !== status) }));
  }

  async function exportCsv() {
    setExporting(true);
    setNotice(null);
    setError(null);
    try {
      const blob = await downloadSalesCsv(query);
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `vendas-paysi-${new Date().toISOString().slice(0, 10)}.csv`;
      document.body.append(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
      setNotice("Exportação concluída. O arquivo traz até 10.000 vendas do filtro atual.");
    } catch (exportError) {
      setError(exportError instanceof ApiRequestError ? exportError.message : "Não foi possível exportar as vendas.");
    } finally {
      setExporting(false);
    }
  }

  const activeFilters = query.statuses.length + (query.method ? 1 : 0) + (query.productId ? 1 : 0) + (query.from ? 1 : 0) + (query.to ? 1 : 0);
  const rows: SaleRow[] = data?.items ?? [];

  return <div className="vd">
    <header className="prod-head">
      <h1>Vendas</h1>
      <button type="button" className="ui-button ui-button-secondary" disabled={exporting} onClick={() => void exportCsv()}>{exporting ? "Exportando…" : "Exportar"}</button>
    </header>

    <section className="mk-search" aria-label="Buscar vendas">
      <div className="mk-search-bar">
        <label className="mk-search-field"><span className="sr-only">Buscar por cliente, e-mail, produto, CPF ou ID da venda</span>
          <input type="search" value={search} onChange={event => setSearch(event.target.value)} placeholder="Buscar por cliente, e-mail, produto, CPF ou ID da venda" /></label>
        <button type="button" className="mk-filter-button" aria-expanded={filtersOpen} aria-controls="vd-filtros" onClick={() => { setDraft(query); setFiltersOpen(open => !open); }}>Filtros{activeFilters ? ` (${activeFilters})` : ""}</button>
      </div>
      {filtersOpen && <div id="vd-filtros" className="mk-filters vd-filters">
        <label className="pe-field"><span>De</span><input type="date" value={draft.from} max={draft.to || undefined} onChange={event => setDraft(current => ({ ...current, from: event.target.value }))} /></label>
        <label className="pe-field"><span>Até</span><input type="date" value={draft.to} min={draft.from || undefined} onChange={event => setDraft(current => ({ ...current, to: event.target.value }))} /></label>
        <label className="pe-field"><span>Produto</span><select value={draft.productId} onChange={event => setDraft(current => ({ ...current, productId: event.target.value }))}><option value="">Todos os produtos</option>{products.map(product => <option key={product.id} value={product.id}>{product.name}</option>)}</select></label>
        <label className="pe-field"><span>Método de pagamento</span><select value={draft.method} onChange={event => setDraft(current => ({ ...current, method: event.target.value as "" | SaleMethod }))}><option value="">Todos</option>{(Object.keys(saleMethodLabel) as SaleMethod[]).map(method => <option key={method} value={method}>{saleMethodLabel[method]}</option>)}</select></label>
        <fieldset className="pe-fieldset vd-status-filter" disabled={query.tab === "approved"}>
          <legend>Status{query.tab === "approved" ? " (use a aba Todas para filtrar)" : ""}</legend>
          <div className="vd-status-grid">{statusOrder.map(status => <label className="mk-terms" key={status}><input type="checkbox" checked={draft.statuses.includes(status)} onChange={event => toggleStatus(status, event.target.checked)} /> {saleStatusLabel[status]}</label>)}</div>
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
      <section className="dash-card" aria-label="Vendas encontradas"><span>Vendas encontradas</span><strong>{data ? data.summary.count : "—"}</strong></section>
      <section className="dash-card" aria-label="Valor líquido"><span>Valor líquido</span><strong>{data ? formatarCentavos(data.summary.netCents) : "—"}</strong></section>
    </div>

    <section className="prod-panel">
      <div className="pe-tabs vd-tabs" role="tablist" aria-label="Situação das vendas">
        <button type="button" role="tab" id="vd-aba-approved" aria-selected={query.tab === "approved"} aria-controls="vd-lista" onClick={() => changeTab("approved")}>Aprovadas</button>
        <button type="button" role="tab" id="vd-aba-all" aria-selected={query.tab === "all"} aria-controls="vd-lista" onClick={() => changeTab("all")}>Todas</button>
      </div>
      <div id="vd-lista" role="tabpanel" aria-labelledby={`vd-aba-${query.tab}`}>
        {loading && !data ? <Skeleton label="Carregando vendas" /> : rows.length === 0 ?
          <EmptyState title="Nenhuma venda encontrada" description={query.q || activeFilters ? "Ajuste a busca ou os filtros para ver outras vendas." : "Quando você fizer a primeira venda, ela aparece aqui."} /> :
          <div className="dash-table-wrap"><table className="prod-table vd-table" aria-busy={loading}>
            <thead><tr><th scope="col">Data</th><th scope="col">Produto</th><th scope="col">Cliente</th><th scope="col">Status</th><th scope="col">Valor líquido</th></tr></thead>
            <tbody>{rows.map(row => <tr key={row.id} className="vd-row" onClick={() => setOpenId(row.id)}>
              <td className="prod-muted">{dateTime.format(new Date(row.createdAt))}</td>
              <td className="prod-muted">{row.productName}{row.offerName ? <small className="vd-offer">{row.offerName}</small> : null}</td>
              <td><button type="button" className="pe-linkbtn vd-buyer" aria-label={`Ver detalhes da venda ${row.code} de ${row.buyerName}`} onClick={event => { event.stopPropagation(); setOpenId(row.id); }}><strong>{row.buyerName}</strong><small>{row.buyerEmail}</small></button></td>
              <td><span className={pillClass(row.status)}>{saleStatusLabel[row.status]}</span><small className="vd-method">{saleMethodLabel[row.method]}</small></td>
              <td>{formatarCentavos(row.netCents)}</td>
            </tr>)}</tbody>
          </table></div>}
        {data && <Paginacao page={data.page} totalPages={data.totalPages} onChange={page => setQuery(current => ({ ...current, page }))} />}
      </div>
    </section>

    <VendaDrawer saleId={openId} onClose={() => setOpenId(null)} onChanged={() => void load(query)} />
  </div>;
}
