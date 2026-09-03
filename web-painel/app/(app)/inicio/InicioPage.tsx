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

function bucketTone(bucket: keyof typeof bucketLabel, cents: number): "neutral" | "success" | "danger" {
  if (bucket === "debt") return cents !== 0 ? "danger" : "neutral";
  if (bucket === "available") return "success";
  return "neutral";
}

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
          <BlocoEmBreve icon={<IconVendas />} title="Vendas hoje" description="O acompanhamento de vendas chega quando o checkout estiver disponível." cta />
          <BlocoEmBreve icon={<IconAssinaturas />} title="Assinaturas" description="A visão de assinaturas ativas e inadimplentes chega em breve." />
          <BlocoEmBreve icon={<IconVendasRecentes />} title="Últimas vendas" description="A lista das vendas mais recentes chega em breve." />
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
      {bucketOrder.map(bucket => {
        const tone = bucketTone(bucket, balance[bucket]);
        return <Cartao key={bucket} className="bucket-tile" data-tone={tone}>
          <span>{bucketLabel[bucket]}</span>
          <strong className={`paysi-valor bucket-valor-${tone}`}>{formatarCentavos(balance[bucket])}</strong>
        </Cartao>;
      })}
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

function BlocoEmBreve({ icon, title, description, cta }: { icon: ReactNode; title: string; description: string; cta?: boolean }) {
  return <Cartao className="em-breve">
    <span className="em-breve-icone" aria-hidden="true">{icon}</span>
    <h2>{title}</h2>
    <p>{description}</p>
    {cta && <Link className="ui-button ui-button-primary" href="/produtos/novo">Criar produto</Link>}
  </Cartao>;
}

function IconVendas() {
  return <svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
    <path d="M3 15.5 7.5 11l3 3L17 6.5" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"/>
    <path d="M12.5 6.5H17V11" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"/>
  </svg>;
}

function IconAssinaturas() {
  return <svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
    <path d="M15.9 6.5A6 6 0 1 0 17 11" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round"/>
    <path d="M15.5 3v3.5H12" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"/>
  </svg>;
}

function IconVendasRecentes() {
  return <svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
    <rect x="4" y="3" width="12" height="14" rx="1.5" stroke="currentColor" strokeWidth="1.6"/>
    <path d="M7 7h6M7 10h6M7 13h3.5" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round"/>
  </svg>;
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
