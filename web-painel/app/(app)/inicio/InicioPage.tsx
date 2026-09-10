"use client";

import Link from "next/link";
import { ReactNode, useEffect, useState } from "react";
import { Cartao, EmptyState, Etiqueta, Skeleton, Tabela, Toast } from "../../../components/ui";
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

  return <>
    <header className="content-header dashboard-page-header">
      <div>
        <span className="paysi-rotulo">Visão geral</span>
        <h1>Início</h1>
        <p>Acompanhe a operação em um só lugar.</p>
      </div>
      {mode === "SELLER" && <label className="dashboard-filter">
        <span>Período</span>
        <select value={period} onChange={event => setPeriod(event.target.value as DashboardPeriodPreset)}>
          {Object.entries(periodLabel).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>
      </label>}
    </header>

    {mode === null ? <Skeleton label="Carregando início" /> :
      mode === "AFFILIATE" ? <Cartao className="dashboard-mode-card">
        <h2>Modo afiliado</h2>
        <p>Este painel mostra a operação de vendedor. A visão do modo afiliado ainda está em construção — troque para "Vender" para acompanhar sua conta.</p>
      </Cartao> : error ? <Toast tone="danger">{error} <button className="toast-action" onClick={() => setReloadKey(value => value + 1)}>Tentar novamente</button></Toast> :
      loading || !dashboard ? <Skeleton label="Carregando dashboard" /> : <div className="dashboard-blocks">
        <Vendas block={dashboard.salesToday} period={dashboard.period.preset} />
        <SaldoBuckets block={dashboard.balance} />
        <Alertas block={dashboard.alerts} />
        <ProximosRecebimentos block={dashboard.nextReceivables} />
        <Assinaturas block={dashboard.subscriptions} />
        <UltimasVendas block={dashboard.recentSales} />
        <AcoesRapidas />
      </div>}
  </>;
}

function Bloco<T>({ title, block, emptyTitle, emptyDescription, className, children }: {
  title: string;
  block: DashboardBlock<T>;
  emptyTitle: string;
  emptyDescription: string;
  className?: string;
  children: (data: T) => ReactNode;
}) {
  return <section className={className} aria-labelledby={`${title}-titulo`}>
    <h2 id={`${title}-titulo`}>{title}</h2>
    {block.state === "ERROR" ? <Toast tone="danger">{block.message ?? "Este bloco está temporariamente indisponível."}</Toast> :
      block.state === "EMPTY" ? <EmptyState headingLevel="h3" title={emptyTitle} description={emptyDescription} action={<Link className="ui-button ui-button-primary" href="/produtos/novo">Criar produto</Link>} /> :
      block.data === undefined ? <Toast tone="danger">Resposta incompleta do dashboard.</Toast> : children(block.data)}
  </section>;
}

function Vendas({ block, period }: { block: DashboardBlock<SalesSummary>; period: DashboardPeriodPreset }) {
  return <Bloco className="dashboard-section dashboard-sales" title="Vendas no período" block={block} emptyTitle={`Nenhuma venda em ${periodLabel[period].toLowerCase()}`} emptyDescription="Crie um produto para começar a vender.">
    {data => <div className="stats stats-dashboard">
      <Cartao className="dashboard-kpi-card"><span>Valor confirmado</span><strong className="paysi-valor">{formatarCentavos(data.amountCents)}</strong></Cartao>
      <Cartao className="dashboard-kpi-card"><span>Quantidade</span><strong>{data.count}</strong></Cartao>
    </div>}
  </Bloco>;
}

function SaldoBuckets({ block }: { block: DashboardBlock<BalanceView> }) {
  return <Bloco className="dashboard-section dashboard-balance" title="Saldo" block={block} emptyTitle="Saldo indisponível" emptyDescription="Ainda não há saldo para exibir.">
    {balance => <div className="stats stats-dashboard">
      {bucketOrder.map(bucket => <Cartao className="dashboard-balance-card" key={bucket}>
        <span>{bucketLabel[bucket]}</span>
        <strong className="paysi-valor">{formatarCentavos(balance[bucket])}</strong>
      </Cartao>)}
    </div>}
  </Bloco>;
}

function Alertas({ block }: { block: DashboardBlock<DashboardAlert[]> }) {
  return <Bloco className="dashboard-section dashboard-alerts" title="Alertas" block={block} emptyTitle="Nenhum alerta no momento" emptyDescription="Sua conta está em dia.">
    {alerts => <div className="alert-list">
      {alerts.map(alert => <Cartao className={`dashboard-alert-card dashboard-alert-card-${alert.tone}`} key={alert.id} role={alert.tone === "danger" ? "alert" : "status"}>
        <div className="ui-labels"><Etiqueta tone={alert.tone}>{alert.tone === "danger" ? "Atenção" : "Aviso"}</Etiqueta></div>
        <h3>{alert.title}</h3>
        <p>{alert.description}</p>
        {alert.actionUrl && (alert.actionUrl.startsWith("/") ? <Link className="ui-button ui-button-secondary" href={alert.actionUrl}>Continuar</Link> : <a className="ui-button ui-button-secondary" href={alert.actionUrl} target="_blank" rel="noopener noreferrer">Continuar verificação</a>)}
      </Cartao>)}
    </div>}
  </Bloco>;
}

function ProximosRecebimentos({ block }: { block: DashboardBlock<UpcomingReceivable[]> }) {
  return <Bloco className="dashboard-section dashboard-receivables" title="Próximos recebimentos" block={block} emptyTitle="Nenhum recebimento previsto" emptyDescription="Quando houver valores a caminho do seu saldo disponível, eles aparecem aqui.">
    {items => <Cartao className="dashboard-table-card"><Tabela caption="Próximos recebimentos" headers={["Data prevista", "Valor"]} rows={items.map((item): ReactNode[] => [
      formatDate(item.availableAt),
      <span key={`${item.availableAt}-${item.amountCents}`} className="paysi-valor">{formatarCentavos(item.amountCents)}</span>,
    ])} /></Cartao>}
  </Bloco>;
}

function Assinaturas({ block }: { block: DashboardBlock<SubscriptionSummary> }) {
  return <Bloco className="dashboard-section dashboard-subscriptions" title="Assinaturas" block={block} emptyTitle="Nenhuma assinatura" emptyDescription="Assinaturas ativas e inadimplentes aparecerão aqui.">
    {data => <div className="stats stats-dashboard">
      <Cartao className="dashboard-kpi-card"><span>Ativas</span><strong>{data.active}</strong></Cartao>
      <Cartao className="dashboard-kpi-card"><span>Em atraso</span><strong>{data.pastDue}</strong></Cartao>
    </div>}
  </Bloco>;
}

function UltimasVendas({ block }: { block: DashboardBlock<RecentSale[]> }) {
  return <Bloco className="dashboard-section dashboard-recent-sales" title="Últimas vendas" block={block} emptyTitle="Nenhuma venda recente" emptyDescription="As vendas confirmadas aparecerão aqui.">
    {items => <Cartao className="dashboard-table-card"><Tabela caption="Últimas vendas" headers={["Comprador", "Valor", "Método", "Status", "Data"]} rows={items.map((item): ReactNode[] => [
      item.buyer,
      <span key={`${item.id}-amount`} className="paysi-valor">{formatarCentavos(item.amountCents)}</span>,
      item.method,
      item.status,
      formatDate(item.occurredAt),
    ])} /></Cartao>}
  </Bloco>;
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(value));
}

function AcoesRapidas() {
  return <section className="dashboard-section dashboard-actions" aria-labelledby="acoes-titulo">
    <h2 id="acoes-titulo">Ações rápidas</h2>
    <div className="ui-actions">
      <Link className="ui-button ui-button-primary" href="/produtos/novo">Criar produto</Link>
      <Link className="ui-button ui-button-secondary" href="/produtos">Ver produtos</Link>
    </div>
  </section>;
}
