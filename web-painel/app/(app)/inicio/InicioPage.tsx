"use client";

import Link from "next/link";
import { ReactNode, useEffect, useState } from "react";
import { Skeleton, Tabela, Toast } from "../../../components/ui";
import { currentSession, SessionCreated } from "../../../lib/sessao";
import {
  BalanceView,
  DashboardAlert,
  DashboardBlock,
  DashboardPeriodPreset,
  DashboardView,
  RecentSale,
  SalesSummary,
  SubscriptionSummary,
  UpcomingReceivable,
  getDashboard,
} from "../../../lib/dashboard";
import { formatarCentavos } from "../../../lib/moeda";

const bucketLabel = {
  guarantee: "Garantia",
  pending: "Pendente",
  reserve: "Reserva",
  available: "Disponível",
  debt: "Débito",
} as const satisfies Record<string, string>;

const bucketOrder = Object.keys(bucketLabel) as (keyof typeof bucketLabel)[];

const periodLabel: Record<DashboardPeriodPreset, string> = {
  today: "Hoje",
  "7d": "Últimos 7 dias",
  "30d": "Últimos 30 dias",
};

export function InicioPage() {
  const [mode, setMode] = useState<SessionCreated["activeMode"] | null>(null);
  const [period, setPeriod] = useState<DashboardPeriodPreset>("today");
  const [dashboard, setDashboard] = useState<DashboardView | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    currentSession().then(session => setMode(session.activeMode)).catch(() => setMode("SELLER"));
  }, []);

  useEffect(() => {
    if (mode !== "SELLER") return;
    let active = true;
    setLoading(true);
    setError(null);
    getDashboard(period)
      .then(result => { if (active) setDashboard(result); })
      .catch(() => { if (active) setError("Não foi possível carregar o dashboard."); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [mode, period, reloadKey]);

  const periods = Object.entries(periodLabel) as [DashboardPeriodPreset, string][];

  return <div className="dash">
    <header className="dash-head">
      <h1>Dashboard</h1>
      {mode === "SELLER" && <div className="dash-period" role="group" aria-label="Período">
        {periods.map(([value, label]) => <button key={value} type="button" aria-pressed={period === value} onClick={() => setPeriod(value)}>{label}</button>)}
      </div>}
    </header>

    {mode === null ? <Skeleton label="Carregando dashboard" /> :
      mode === "AFFILIATE" ? <section className="dash-card dash-note">
        <h2>Modo afiliado</h2>
        <p>Este painel mostra a operação de vendedor. A visão do modo afiliado ainda está em construção — troque para "Vender" para acompanhar sua conta.</p>
      </section> : error ? <Toast tone="danger">{error} <button className="toast-action" onClick={() => setReloadKey(value => value + 1)}>Tentar novamente</button></Toast> :
      loading || !dashboard ? <Skeleton label="Carregando dashboard" /> : <div className="dash-grid">
        <Alertas block={dashboard.alerts} />
        <Vendas block={dashboard.salesToday} period={dashboard.period.preset} subscriptions={dashboard.subscriptions} />
        <SaldoBuckets block={dashboard.balance} />
        <UltimasVendas block={dashboard.recentSales} />
        <ProximosRecebimentos block={dashboard.nextReceivables} />
      </div>}
  </div>;
}

function Bloco<T>({ title, block, emptyTitle, emptyDescription, className, children }: {
  title: string;
  block: DashboardBlock<T>;
  emptyTitle: string;
  emptyDescription: string;
  className?: string;
  children: (data: T) => ReactNode;
}) {
  return <section className={`dash-card ${className ?? ""}`} aria-labelledby={`${title}-titulo`}>
    <h2 id={`${title}-titulo`}>{title}</h2>
    {block.state === "ERROR" ? <Toast tone="danger">{block.message ?? "Este bloco está temporariamente indisponível."}</Toast> :
      block.state === "EMPTY" ? <div className="dash-empty"><h3>{emptyTitle}</h3><p>{emptyDescription}</p></div> :
      block.data === undefined ? <Toast tone="danger">Resposta incompleta do dashboard.</Toast> : children(block.data)}
  </section>;
}

function Vendas({ block, period, subscriptions }: { block: DashboardBlock<SalesSummary>; period: DashboardPeriodPreset; subscriptions: DashboardBlock<SubscriptionSummary> }) {
  const subs = subscriptions.data;
  return <Bloco className="dash-wide" title="Vendas" block={block} emptyTitle={`Nenhuma venda em ${periodLabel[period].toLowerCase()}`} emptyDescription="Crie um produto e compartilhe o link de checkout para começar a vender.">
    {data => <div className="dash-kpis">
      <div className="dash-kpi dash-kpi-green"><span>Valor confirmado</span><strong className="paysi-valor">{formatarCentavos(data.amountCents)}</strong></div>
      <div className="dash-kpi dash-kpi-blue"><span>Vendas</span><strong>{data.count}</strong></div>
      <div className="dash-kpi dash-kpi-violet"><span>Assinaturas ativas</span><strong>{subs ? subs.active : "—"}</strong></div>
      <div className="dash-kpi dash-kpi-red"><span>Em atraso</span><strong>{subs ? subs.pastDue : "—"}</strong></div>
    </div>}
  </Bloco>;
}

function SaldoBuckets({ block }: { block: DashboardBlock<BalanceView> }) {
  return <Bloco className="dash-wide" title="Saldo" block={block} emptyTitle="Saldo indisponível" emptyDescription="Ainda não há saldo para exibir.">
    {balance => <div className="dash-balance">
      {bucketOrder.map(bucket => <div className={`dash-bal dash-bal-${bucket}`} key={bucket}>
        <span>{bucketLabel[bucket]}</span>
        <strong className="paysi-valor">{formatarCentavos(balance[bucket])}</strong>
      </div>)}
    </div>}
  </Bloco>;
}

function Alertas({ block }: { block: DashboardBlock<DashboardAlert[]> }) {
  if (!block.data?.length) return null;
  return <section className="dash-wide dash-alerts" aria-label="Alertas">
    {block.data.map(alert => <div className={`dash-alert dash-alert-${alert.tone}`} key={alert.id} role={alert.tone === "danger" ? "alert" : "status"}>
      <div><h2>{alert.title}</h2><p>{alert.description}</p></div>
      {alert.actionUrl && (alert.actionUrl.startsWith("/") ? <Link className="ui-button ui-button-secondary" href={alert.actionUrl}>Continuar</Link> : <a className="ui-button ui-button-secondary" href={alert.actionUrl} target="_blank" rel="noopener noreferrer">Continuar verificação</a>)}
    </div>)}
  </section>;
}

function ProximosRecebimentos({ block }: { block: DashboardBlock<UpcomingReceivable[]> }) {
  return <Bloco className="dash-side" title="Próximos recebimentos" block={block} emptyTitle="Nenhum recebimento previsto" emptyDescription="Valores a caminho do seu saldo disponível aparecem aqui.">
    {items => <ul className="dash-list">{items.map(item => <li key={`${item.availableAt}-${item.amountCents}`}>
      <span>{formatDate(item.availableAt)}</span><strong className="paysi-valor">{formatarCentavos(item.amountCents)}</strong>
    </li>)}</ul>}
  </Bloco>;
}

function UltimasVendas({ block }: { block: DashboardBlock<RecentSale[]> }) {
  return <Bloco className="dash-main" title="Últimas vendas" block={block} emptyTitle="Nenhuma venda recente" emptyDescription="As vendas confirmadas aparecerão aqui.">
    {items => <div className="dash-table-wrap"><Tabela caption="Últimas vendas" headers={["Comprador", "Valor", "Método", "Status", "Data"]} rows={items.map((item): ReactNode[] => [
      item.buyer,
      <span key={`${item.id}-amount`} className="paysi-valor">{formatarCentavos(item.amountCents)}</span>,
      item.method,
      item.status,
      formatDate(item.occurredAt),
    ])} /></div>}
  </Bloco>;
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(value));
}
