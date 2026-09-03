"use client";

import Link from "next/link";
import { ReactNode, useCallback, useEffect, useState } from "react";
import { currentSession, SessionCreated } from "../../../lib/sessao";
import {
  BalanceView,
  DashboardAlert,
  Recebivel,
  dashboardAlerts,
  fetchUpcomingReceivables,
  getBalance,
  getKyc,
} from "../../../lib/dashboard";
import { formatarCentavos } from "../../../lib/moeda";
import { Cartao, EmptyState, Etiqueta, Skeleton, Tabela, Toast } from "../../../components/ui";

const bucketLabel = {
  guarantee: "Garantia",
  pending: "Pendente",
  reserve: "Reserva",
  available: "Disponível",
  debt: "Débito",
} as const satisfies Record<string, string>;

const bucketOrder = Object.keys(bucketLabel) as (keyof typeof bucketLabel)[];

export function InicioPage() {
  const [mode, setMode] = useState<SessionCreated["activeMode"] | null>(null);

  useEffect(() => {
    currentSession().then(session => setMode(session.activeMode)).catch(() => setMode("SELLER"));
  }, []);

  return <>
    <header className="content-header">
      <div><h1>Início</h1><p>Acompanhe a operação em um só lugar.</p></div>
    </header>

    {mode === null ? <Skeleton label="Carregando início" /> :
      mode === "AFFILIATE" ? <Cartao>
        <h2>Modo afiliado</h2>
        <p>Este painel mostra a operação de vendedor. A visão do modo afiliado ainda está em construção — troque para "Vender" para acompanhar sua conta.</p>
      </Cartao> : <div className="dashboard-blocks">
        <SaldoBuckets />
        <Alertas />
        <ProximosRecebimentos />

        <section className="stats" aria-label="Vendas e assinaturas">
          <BlocoEmBreve title="Vendas hoje" description="O acompanhamento de vendas chega quando o checkout estiver disponível." cta />
          <BlocoEmBreve title="Assinaturas" description="A visão de assinaturas ativas e inadimplentes chega em breve." />
          <BlocoEmBreve title="Últimas vendas" description="A lista das vendas mais recentes chega em breve." />
        </section>

        <AcoesRapidas />
      </div>}
  </>;
}

function SaldoBuckets() {
  const [balance, setBalance] = useState<BalanceView | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setBalance(await getBalance());
    } catch {
      setError("Não foi possível carregar o saldo.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  return <section aria-labelledby="saldo-titulo">
    <h2 id="saldo-titulo">Saldo</h2>
    {error && <Toast tone="danger">{error} <button className="toast-action" onClick={() => void load()}>Tentar novamente</button></Toast>}
    {loading ? <Skeleton label="Carregando saldo" /> : balance && <div className="stats">
      {bucketOrder.map(bucket => <Cartao key={bucket}>
        <span>{bucketLabel[bucket]}</span>
        <strong className="paysi-valor">{formatarCentavos(balance[bucket])}</strong>
      </Cartao>)}
    </div>}
  </section>;
}

function Alertas() {
  const [alerts, setAlerts] = useState<DashboardAlert[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [balance, kyc] = await Promise.all([getBalance(), getKyc()]);
      setAlerts(dashboardAlerts(balance, kyc));
    } catch {
      setError("Não foi possível carregar os alertas.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  return <section aria-labelledby="alertas-titulo">
    <h2 id="alertas-titulo">Alertas</h2>
    {error && <Toast tone="danger">{error} <button className="toast-action" onClick={() => void load()}>Tentar novamente</button></Toast>}
    {loading ? <Skeleton label="Carregando alertas" /> :
      alerts?.length === 0 ? <EmptyState title="Nenhum alerta no momento" description="Sua conta está em dia." /> :
      alerts && <div className="alert-list">
        {alerts.map(alert => <Cartao key={alert.id} role={alert.tone === "danger" ? "alert" : "status"}>
          <div className="ui-labels"><Etiqueta tone={alert.tone}>{alert.tone === "danger" ? "Atenção" : "Aviso"}</Etiqueta></div>
          <h3>{alert.title}</h3>
          <p>{alert.description}</p>
          {alert.actionUrl && <a className="ui-button ui-button-secondary" href={alert.actionUrl} target="_blank" rel="noopener noreferrer">Continuar verificação</a>}
        </Cartao>)}
      </div>}
  </section>;
}

function ProximosRecebimentos() {
  const [items, setItems] = useState<Recebivel[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setItems(await fetchUpcomingReceivables());
    } catch {
      setError("Não foi possível carregar os próximos recebimentos.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  return <section aria-labelledby="recebimentos-titulo">
    <h2 id="recebimentos-titulo">Próximos recebimentos</h2>
    {error && <Toast tone="danger">{error} <button className="toast-action" onClick={() => void load()}>Tentar novamente</button></Toast>}
    {loading ? <Skeleton label="Carregando próximos recebimentos" /> :
      items?.length === 0 ? <EmptyState title="Nenhum recebimento previsto" description="Quando houver valores a caminho do seu saldo disponível, eles aparecem aqui." /> :
      items && <Tabela
        caption="Próximos recebimentos"
        headers={["Data prevista", "Valor"]}
        rows={items.map((item): ReactNode[] => [
          new Intl.DateTimeFormat("pt-BR").format(new Date(item.availableAt)),
          <span className="paysi-valor">{formatarCentavos(item.amountCents)}</span>,
        ])}
      />}
  </section>;
}

function BlocoEmBreve({ title, description, cta }: { title: string; description: string; cta?: boolean }) {
  return <Cartao>
    <EmptyState
      title={title}
      description={description}
      action={cta ? <Link className="ui-button ui-button-primary" href="/produtos/novo">Criar produto</Link> : undefined}
    />
  </Cartao>;
}

function AcoesRapidas() {
  return <section aria-labelledby="acoes-titulo">
    <h2 id="acoes-titulo">Ações rápidas</h2>
    <div className="ui-actions">
      <Link className="ui-button ui-button-primary" href="/produtos/novo">Criar produto</Link>
      <Link className="ui-button ui-button-secondary" href="/produtos">Ver produtos</Link>
    </div>
  </section>;
}
