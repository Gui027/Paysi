"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { ApiRequestError } from "../../lib/api";
import { AdminDashboard, RevenuePoint, adminLogout, getAdminDashboard } from "../../lib/admin-dashboard";

const periods = [7, 30, 90, 365] as const;
const statusLabel = { ACTIVE: "Ativa", LIMITED: "Limitada", SUSPENDED: "Suspensa", CLOSED: "Encerrada" };

function money(cents: number) {
  return new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(cents / 100);
}

function date(value: string) {
  return new Intl.DateTimeFormat("pt-BR", { dateStyle: "medium" }).format(new Date(value));
}

function RevenueChart({ points }: { points: RevenuePoint[] }) {
  const width = 1000;
  const height = 250;
  const padding = 24;
  const max = Math.max(...points.map(item => item.revenueCents), 1);
  const coordinates = points.map((point, index) => ({
    ...point,
    x: padding + (index / Math.max(points.length - 1, 1)) * (width - padding * 2),
    y: height - padding - (point.revenueCents / max) * (height - padding * 2),
  }));
  const line = coordinates.map(point => `${point.x},${point.y}`).join(" ");
  const area = coordinates.length ? `${padding},${height - padding} ${line} ${width - padding},${height - padding}` : "";
  const labels = coordinates.filter((_, index) => index === 0 || index === coordinates.length - 1
    || index % Math.max(1, Math.floor(coordinates.length / 4)) === 0);

  return <div className="admin-chart-wrap">
    <svg className="admin-chart" viewBox={`0 0 ${width} ${height}`} role="img"
      aria-label="Gráfico do faturamento diário no período selecionado">
      <defs><linearGradient id="admin-area" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#7357ff" stopOpacity=".35"/><stop offset="1" stopColor="#7357ff" stopOpacity="0"/></linearGradient></defs>
      {[0.25, 0.5, 0.75, 1].map(level => <line key={level} x1={padding} x2={width - padding}
        y1={padding + level * (height - padding * 2)} y2={padding + level * (height - padding * 2)} className="admin-chart-grid" />)}
      {area && <polygon points={area} fill="url(#admin-area)" />}
      {line && <polyline points={line} className="admin-chart-line" />}
      {coordinates.map(point => <circle key={point.date} cx={point.x} cy={point.y} r="4" className="admin-chart-dot"><title>{`${point.date}: ${money(point.revenueCents)} em ${point.sales} vendas`}</title></circle>)}
    </svg>
    <div className="admin-chart-labels" aria-hidden="true">
      {labels.map(point => <span key={point.date} style={{ left: `${(point.x / width) * 100}%` }}>{new Date(`${point.date}T12:00:00Z`).toLocaleDateString("pt-BR", { day: "2-digit", month: "short" })}</span>)}
    </div>
  </div>;
}

export default function AdminPage() {
  const router = useRouter();
  const [days, setDays] = useState<number>(30);
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [page, setPage] = useState(0);
  const [data, setData] = useState<AdminDashboard | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    const timer = window.setTimeout(() => { setDebouncedQuery(query); setPage(0); }, 300);
    return () => window.clearTimeout(timer);
  }, [query]);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      setData(await getAdminDashboard(days, debouncedQuery, page));
    } catch (cause) {
      if (cause instanceof ApiRequestError && cause.status === 401) {
        router.replace("/entrar");
        return;
      }
      setError("Não foi possível carregar os dados administrativos.");
    } finally {
      setLoading(false);
    }
  }, [days, debouncedQuery, page, router]);

  useEffect(() => { void load(); }, [load]);

  const periodLabel = useMemo(() => days === 365 ? "12 meses" : `${days} dias`, [days]);

  async function logout() {
    try { await adminLogout(); } finally { router.replace("/entrar"); router.refresh(); }
  }

  return <div className="admin-shell">
    <header className="admin-topbar">
      <div className="admin-wordmark"><span className="admin-logo-mark">P</span><div><strong>Paysi</strong><small>Admin</small></div></div>
      <div className="admin-profile"><span className="admin-avatar">A</span><div><strong>Administrador</strong><small>Acesso total de leitura</small></div><button type="button" onClick={logout}>Sair</button></div>
    </header>
    <main className="admin-content">
      <div className="admin-heading"><div><span className="admin-eyebrow">Visão geral</span><h1>Dashboard administrativo</h1><p>Dados consolidados de toda a operação Paysi.</p></div>
        <div className="admin-period" aria-label="Período do relatório">{periods.map(period => <button key={period}
          className={days === period ? "active" : ""} aria-pressed={days === period}
          onClick={() => { setDays(period); setPage(0); }}>{period === 365 ? "1 ano" : `${period} dias`}</button>)}</div>
      </div>

      {error && <div className="admin-error" role="alert">{error}<button onClick={load}>Tentar novamente</button></div>}
      {loading && !data ? <div className="admin-loading" role="status">Carregando indicadores…</div> : data && <>
        <section className="admin-kpis" aria-label="Indicadores principais">
          <article><span>Faturamento total</span><strong>{money(data.summary.totalRevenueCents)}</strong><small>Desde o início da operação</small></article>
          <article><span>Faturamento · {periodLabel}</span><strong>{money(data.summary.periodRevenueCents)}</strong><small>{data.summary.paidSales.toLocaleString("pt-BR")} vendas confirmadas</small></article>
          <article><span>Assinaturas ativas</span><strong>{data.summary.activeSubscriptions.toLocaleString("pt-BR")}</strong><small>Ativas e em período de teste</small></article>
          <article><span>Novas contas · {periodLabel}</span><strong>{data.summary.newAccounts.toLocaleString("pt-BR")}</strong><small>Criadas no período</small></article>
        </section>

        <section className="admin-panel admin-revenue-panel">
          <header><div><span className="admin-eyebrow">Desempenho</span><h2>Evolução do faturamento</h2></div><strong>{money(data.summary.periodRevenueCents)}</strong></header>
          <RevenueChart points={data.revenueTimeline} />
        </section>

        <section className="admin-panel admin-accounts-panel">
          <header><div><span className="admin-eyebrow">Clientes</span><h2>Contas da plataforma</h2><p>{data.totalAccounts.toLocaleString("pt-BR")} contas encontradas</p></div>
            <label className="admin-search"><span className="sr-only">Buscar conta</span><input type="search" value={query}
              onChange={(event) => setQuery(event.target.value)} placeholder="Buscar por nome, e-mail ou ID" /></label></header>
          <div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>Conta</th><th>Status</th><th>Faturamento</th><th>Vendas</th><th>Criada em</th></tr></thead><tbody>
            {data.accounts.map(account => <tr key={account.id}><td><strong>{account.name}</strong><span>{account.email}</span><small>{account.id}</small></td>
              <td><span className={`admin-status admin-status-${account.status.toLowerCase()}`}>{statusLabel[account.status]}</span></td>
              <td><strong>{money(account.revenueCents)}</strong></td><td>{account.paidSales.toLocaleString("pt-BR")}</td><td>{date(account.createdAt)}</td></tr>)}
          </tbody></table>{data.accounts.length === 0 && <div className="admin-empty">Nenhuma conta encontrada.</div>}</div>
          <footer><span>Página {data.totalPages === 0 ? 0 : data.page + 1} de {data.totalPages}</span><div><button disabled={data.page === 0 || loading} onClick={() => setPage(current => current - 1)}>Anterior</button><button disabled={data.page + 1 >= data.totalPages || loading} onClick={() => setPage(current => current + 1)}>Próxima</button></div></footer>
        </section>
      </>}
    </main>
  </div>;
}
