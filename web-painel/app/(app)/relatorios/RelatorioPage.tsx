"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { EmptyState, Skeleton, Toast } from "../../../components/ui";
import { Paginacao } from "../../../components/Paginacao";
import { ApiRequestError } from "../../../lib/api";
import { formatarCentavos } from "../../../lib/moeda";
import { listProducts, Product } from "../../../lib/produtos";
import {
  emptyReportQuery, formatCell, futurePresets, getReport, pastPresets, PeriodPreset, presetRange, Report, ReportQuery, whatsappLink,
} from "../../../lib/relatorios";
import { ExportarRelatorio } from "./ExportarRelatorio";
import { GraficoBarras } from "./GraficoBarras";
import { PeriodValue, SeletorPeriodo } from "./SeletorPeriodo";

type Config = {
  title: string;
  data: string;
  future?: boolean;
  preset: PeriodPreset;
  tabs?: readonly (readonly [string, string])[];
  chart?: { first: string; second?: string };
  search?: boolean;
  cards?: readonly (readonly [string, string])[];
  recovery?: boolean;
  export?: boolean;
  empty: string;
};

const coproducao = [["co-producao-recebida", "Receita recebida"], ["co-producao-enviada", "Receita enviada"]] as const;
const CONFIG: Record<string, Config> = {
  "co-producao-recebida": { title: "Receita de co-produção", data: "co-producao-recebida", preset: "all", tabs: coproducao, chart: { first: "Total" }, export: true, empty: "A co-produção ainda não está disponível na Paysi. Quando ela existir, a receita dividida com o produtor aparece aqui." },
  "co-producao-enviada": { title: "Receita de co-produção", data: "co-producao-enviada", preset: "all", tabs: coproducao, chart: { first: "Total" }, export: true, empty: "A co-produção ainda não está disponível na Paysi. Quando ela existir, a receita dividida com o produtor aparece aqui." },
  produto: { title: "Receita por produto", data: "produto", preset: "all", chart: { first: "Total líquido" }, export: true, empty: "Nenhuma venda aprovada no período." },
  afiliado: { title: "Receita por afiliado", data: "afiliado", preset: "all", chart: { first: "Comissões" }, export: true, empty: "Nenhuma venda de afiliado no período." },
  abandonadas: { title: "Vendas abandonadas", data: "abandonadas", preset: "all", search: true, export: true, empty: "Nenhuma venda abandonada no período." },
  alunos: { title: "Engajamento dos alunos", data: "alunos", preset: "all", empty: "O engajamento dos alunos aparece aqui quando a área de membros estiver disponível." },
  "saldo-receber": { title: "Saldo a receber", data: "saldo-receber", future: true, preset: "next30", tabs: [["card", "Cartão de crédito"], ["offline", "Pix e Boleto"], ["international", "Internacional"]], chart: { first: "Total" }, export: true, empty: "Nenhum valor a receber no período." },
  "recebiveis-cartao": { title: "Recebíveis de cartão", data: "recebiveis-cartao", future: true, preset: "next30", chart: { first: "A receber", second: "Efeito de contrato" }, cards: [["receivable", "A receber"], ["contract", "Efeito de contrato"]], export: true, empty: "Nenhum recebível de cartão no período." },
  "assinaturas-canceladas": { title: "Assinaturas canceladas", data: "assinaturas-canceladas", preset: "all", search: true, export: true, empty: "Nenhuma assinatura cancelada no período." },
  "agente-recuperador": { title: "Agente recuperador de vendas", data: "abandonadas", preset: "7d", search: true, recovery: true, empty: "Nenhuma venda para recuperar nos últimos 7 dias." },
};

function initialPeriod(preset: PeriodPreset): PeriodValue {
  return { preset, ...presetRange(preset, new Date()) };
}

export function RelatorioPage({ routeId }: { routeId: string }) {
  const config = CONFIG[routeId];
  const fileKey = config?.data ?? routeId;
  const [tab, setTab] = useState(config?.tabs?.[0][0] ?? "");
  const [period, setPeriod] = useState<PeriodValue>(() => initialPeriod(config?.preset ?? "all"));
  const [search, setSearch] = useState("");
  const [term, setTerm] = useState("");
  const [productId, setProductId] = useState("");
  const [page, setPage] = useState(1);
  const [data, setData] = useState<Report | null>(null);
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const requestId = useRef(0);

  const isCoproduction = routeId.startsWith("co-producao");
  const dataId = isCoproduction ? tab : config?.data ?? routeId;
  const query: ReportQuery = { ...emptyReportQuery, from: period.from, to: period.to, productId, q: term, tab: isCoproduction ? "" : tab, page };

  const load = useCallback(async (id: string, current: ReportQuery) => {
    const request = ++requestId.current;
    setLoading(true);
    setError(null);
    try {
      const result = await getReport(id, current);
      if (request === requestId.current) setData(result);
    } catch (loadError) {
      if (request === requestId.current) setError(loadError instanceof ApiRequestError ? loadError.message : "Não foi possível carregar o relatório.");
    } finally {
      if (request === requestId.current) setLoading(false);
    }
  }, []);

  useEffect(() => { if (config) void load(dataId, query); },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [dataId, period.from, period.to, productId, term, page, tab]);
  useEffect(() => { if (config?.search) listProducts().then(result => setProducts(result.items)).catch(() => undefined); }, [config?.search]);
  useEffect(() => { const timer = setTimeout(() => { setTerm(search); setPage(1); }, 300); return () => clearTimeout(timer); }, [search]);

  if (!config) return <div className="rel"><EmptyState title="Relatório não encontrado" description="Escolha um dos relatórios disponíveis." /></div>;

  const columns = data?.columns ?? [];
  const rows = data?.rows ?? [];
  const phoneIndex = columns.findIndex(column => column.key === "phone");
  const clientIndex = columns.findIndex(column => column.key === "client");
  const productIndex = columns.findIndex(column => column.key === "product");

  return <div className="rel">
    <header className="rel-head">
      <div className="rel-title"><Link href="/relatorios" className="rel-back" aria-label="Voltar para Relatórios">←</Link><h1>{config.title}</h1></div>
      {config.export && <ExportarRelatorio id={dataId} query={query} fileName={fileKey.replaceAll("-", "_")} onNotice={setNotice} onError={setError} />}
    </header>

    {error && <Toast tone="danger">{error} <button className="toast-action" onClick={() => void load(dataId, query)}>Tentar novamente</button></Toast>}
    {notice && <Toast>{notice} <button className="toast-action" onClick={() => setNotice(null)}>Fechar</button></Toast>}

    {config.cards && <div className="vd-summary">
      {config.cards.map(([key, label]) => <section className="dash-card" key={key} aria-label={label}><span>{label}</span><strong>{data ? formatarCentavos(data.totals[key] ?? 0) : "—"}</strong></section>)}
    </div>}

    <section className="prod-panel rel-panel">
      <div className="rel-toolbar">
        {config.tabs ? <div className="pe-tabs rel-tabs" role="tablist" aria-label="Tipo de relatório">
          {config.tabs.map(([value, label]) => <button key={value} type="button" role="tab" aria-selected={tab === value} onClick={() => { setTab(value); setPage(1); }}>{label}</button>)}
        </div> : config.search ? <label className="mk-search-field rel-search"><span className="sr-only">Buscar por cliente, e-mail ou produto</span>
          <input type="search" value={search} onChange={event => setSearch(event.target.value)} placeholder="Buscar…" /></label> : <span />}
        <div className="rel-filters">
          {config.search && <label className="rel-select"><span className="sr-only">Produto</span>
            <select value={productId} onChange={event => { setProductId(event.target.value); setPage(1); }}>
              <option value="">Todos os produtos</option>{products.map(product => <option key={product.id} value={product.id}>{product.name}</option>)}
            </select></label>}
          <SeletorPeriodo value={period} presets={config.future ? futurePresets : pastPresets} onChange={value => { setPeriod(value); setPage(1); }} />
        </div>
      </div>

      {config.chart && data && rows.length > 0 && <GraficoBarras chart={data.chart} label={`Gráfico: ${config.title}`} firstName={config.chart.first} secondName={config.chart.second} />}

      {loading && !data ? <Skeleton label="Carregando relatório" /> : rows.length === 0 ? <EmptyState title="Sem dados" description={config.empty} /> :
        <div className="dash-table-wrap"><table className="prod-table vd-table" aria-busy={loading}>
          <thead><tr>{columns.map(column => <th key={column.key} scope="col">{column.label}</th>)}{config.recovery && <th scope="col">Ação</th>}</tr></thead>
          <tbody>{rows.map((row, index) => <tr key={index}>
            {columns.map((column, position) => <td key={column.key} className={position === 0 || column.type === "text" ? "prod-muted" : undefined}>{formatCell(column, row[position])}</td>)}
            {config.recovery && <td>{(() => {
              const link = phoneIndex >= 0 ? whatsappLink(String(row[phoneIndex]), String(row[clientIndex]), String(row[productIndex])) : null;
              return link ? <a className="ui-button ui-button-secondary rel-wa" href={link} target="_blank" rel="noopener noreferrer">Chamar no WhatsApp</a> : <span className="prod-muted">Sem telefone</span>;
            })()}</td>}
          </tr>)}</tbody>
        </table></div>}
      {data && <Paginacao page={data.page} totalPages={data.totalPages} onChange={setPage} />}
    </section>
  </div>;
}
