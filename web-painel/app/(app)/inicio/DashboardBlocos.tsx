import Link from "next/link";
import { ReactNode } from "react";
import { Toast } from "../../../components/ui";
import { BalanceView, DashboardAlert, DashboardBlock, DashboardPeriodPreset, UpcomingReceivable } from "../../../lib/dashboard";
import { formatarCentavos } from "../../../lib/moeda";

export const periodLabel: Record<DashboardPeriodPreset, string> = {
  today: "Hoje",
  "7d": "Últimos 7 dias",
  "30d": "Últimos 30 dias",
};

const bucketLabel = {
  guarantee: "Garantia",
  pending: "Pendente",
  reserve: "Reserva",
  available: "Disponível",
  debt: "Débito",
} as const satisfies Record<string, string>;

const bucketOrder = Object.keys(bucketLabel) as (keyof typeof bucketLabel)[];

export function formatDate(value: string) {
  return new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(value));
}

function slug(text: string) {
  return text.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase().replace(/[^a-z0-9]+/g, "-");
}

/** Cartão de um bloco do dashboard: mostra erro, estado vazio ou o conteúdo, sem derrubar os demais. */
export function Bloco<T>({ title, block, emptyTitle, emptyDescription, className, children }: {
  title: string;
  block: DashboardBlock<T>;
  emptyTitle: string;
  emptyDescription: ReactNode;
  className?: string;
  children: (data: T) => ReactNode;
}) {
  return <section className={`dash-card ${className ?? ""}`} aria-labelledby={`bloco-${slug(title)}`}>
    <h2 id={`bloco-${slug(title)}`}>{title}</h2>
    {block.state === "ERROR" ? <Toast tone="danger">{block.message ?? "Este bloco está temporariamente indisponível."}</Toast> :
      block.state === "EMPTY" ? <div className="dash-empty"><h3>{emptyTitle}</h3><p>{emptyDescription}</p></div> :
      block.data === undefined ? <Toast tone="danger">Resposta incompleta do dashboard.</Toast> : children(block.data)}
  </section>;
}

export function SaldoBuckets({ block }: { block: DashboardBlock<BalanceView> }) {
  return <Bloco className="dash-wide" title="Saldo" block={block} emptyTitle="Saldo indisponível" emptyDescription="Ainda não há saldo para exibir.">
    {balance => <div className="dash-balance">
      {bucketOrder.map(bucket => <div className={`dash-bal dash-bal-${bucket}`} key={bucket}>
        <span>{bucketLabel[bucket]}</span>
        <strong className="paysi-valor">{formatarCentavos(balance[bucket])}</strong>
      </div>)}
    </div>}
  </Bloco>;
}

export function Alertas({ block }: { block: DashboardBlock<DashboardAlert[]> }) {
  if (!block.data?.length) return null;
  return <section className="dash-wide dash-alerts" aria-label="Alertas">
    {block.data.map(alert => <div className={`dash-alert dash-alert-${alert.tone}`} key={alert.id} role={alert.tone === "danger" ? "alert" : "status"}>
      <div><h2>{alert.title}</h2><p>{alert.description}</p></div>
      {alert.actionUrl && (alert.actionUrl.startsWith("/") ? <Link className="ui-button ui-button-secondary" href={alert.actionUrl}>Continuar</Link> : <a className="ui-button ui-button-secondary" href={alert.actionUrl} target="_blank" rel="noopener noreferrer">Continuar verificação</a>)}
    </div>)}
  </section>;
}

export function ProximosRecebimentos({ block, title = "Próximos recebimentos", emptyDescription = "Valores a caminho do seu saldo disponível aparecem aqui." }: {
  block: DashboardBlock<UpcomingReceivable[]>; title?: string; emptyDescription?: string;
}) {
  return <Bloco className="dash-side" title={title} block={block} emptyTitle="Nenhum recebimento previsto" emptyDescription={emptyDescription}>
    {items => <ul className="dash-list">{items.map(item => <li key={`${item.availableAt}-${item.amountCents}`}>
      <span>{formatDate(item.availableAt)}</span><strong className="paysi-valor">{formatarCentavos(item.amountCents)}</strong>
    </li>)}</ul>}
  </Bloco>;
}
