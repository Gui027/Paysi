"use client";

import { ReactNode, useEffect, useState } from "react";
import { Skeleton, Tabela, Toast } from "../../../components/ui";
import { currentSession, SessionCreated } from "../../../lib/sessao";
import {
  AffiliateDashboardView,
  DashboardBlock,
  DashboardPeriodPreset,
  DashboardView,
  RecentSale,
  SalesSummary,
  SubscriptionSummary,
  getAffiliateDashboard,
  getDashboard,
} from "../../../lib/dashboard";
import { formatarCentavos } from "../../../lib/moeda";
import { AfiliadoInicio } from "./AfiliadoInicio";
import { Alertas, Bloco, formatDate, periodLabel, ProximosRecebimentos, SaldoBuckets } from "./DashboardBlocos";

type Loaded = { mode: "SELLER"; data: DashboardView } | { mode: "AFFILIATE"; data: AffiliateDashboardView };

export function InicioPage() {
  const [mode, setMode] = useState<SessionCreated["activeMode"] | null>(null);
  const [period, setPeriod] = useState<DashboardPeriodPreset>("today");
  const [loaded, setLoaded] = useState<Loaded | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    currentSession().then(session => setMode(session.activeMode)).catch(() => setMode("SELLER"));
  }, []);

  useEffect(() => {
    if (mode === null) return;
    let active = true;
    setLoading(true);
    setError(null);
    const request: Promise<Loaded> = mode === "AFFILIATE"
      ? getAffiliateDashboard(period).then(data => ({ mode: "AFFILIATE" as const, data }))
      : getDashboard(period).then(data => ({ mode: "SELLER" as const, data }));
    request
      .then(result => { if (active) setLoaded(result); })
      .catch(() => { if (active) setError("Não foi possível carregar o dashboard."); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [mode, period, reloadKey]);

  const periods = Object.entries(periodLabel) as [DashboardPeriodPreset, string][];
  const ready = loaded !== null && loaded.mode === mode;

  return <div className="dash">
    <header className="dash-head">
      <h1>Dashboard</h1>
      {mode !== null && <div className="dash-period" role="group" aria-label="Período">
        {periods.map(([value, label]) => <button key={value} type="button" aria-pressed={period === value} onClick={() => setPeriod(value)}>{label}</button>)}
      </div>}
    </header>

    {mode === null ? <Skeleton label="Carregando dashboard" /> :
      error ? <Toast tone="danger">{error} <button className="toast-action" onClick={() => setReloadKey(value => value + 1)}>Tentar novamente</button></Toast> :
      loading || !ready ? <Skeleton label="Carregando dashboard" /> :
      loaded.mode === "AFFILIATE" ? <AfiliadoInicio dashboard={loaded.data} /> : <div className="dash-grid">
        <Alertas block={loaded.data.alerts} />
        <Vendas block={loaded.data.salesToday} period={loaded.data.period.preset} subscriptions={loaded.data.subscriptions} />
        <SaldoBuckets block={loaded.data.balance} />
        <UltimasVendas block={loaded.data.recentSales} />
        <ProximosRecebimentos block={loaded.data.nextReceivables} />
      </div>}
  </div>;
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
