import Link from "next/link";
import { ReactNode } from "react";
import { Tabela } from "../../../components/ui";
import { AffiliateDashboardView, commissionStatusLabel } from "../../../lib/dashboard";
import { formatarCentavos } from "../../../lib/moeda";
import { Alertas, Bloco, formatDate, periodLabel, ProximosRecebimentos, SaldoBuckets } from "./DashboardBlocos";

/** Dashboard do modo afiliado: o que rendeu em comissões, o tráfego dos links e o que está a caminho do saldo. */
export function AfiliadoInicio({ dashboard }: { dashboard: AffiliateDashboardView }) {
  const preset = dashboard.period.preset;
  return <div className="dash-grid">
    <Alertas block={dashboard.alerts} />

    <Bloco className="dash-wide" title="Suas comissões" block={dashboard.earnings}
      emptyTitle={`Nenhuma comissão em ${periodLabel[preset].toLowerCase()}`}
      emptyDescription={<>Escolha um produto na <Link href="/vitrine">vitrine</Link>, pegue o seu link e divulgue para começar a ganhar.</>}>
      {data => <div className="dash-kpis">
        <div className="dash-kpi dash-kpi-green"><span>Comissões confirmadas</span><strong className="paysi-valor">{formatarCentavos(data.commissionCents)}</strong></div>
        <div className="dash-kpi dash-kpi-blue"><span>Vendas indicadas</span><strong>{data.sales}</strong></div>
        <div className="dash-kpi dash-kpi-violet"><span>Cliques nos seus links</span><strong>{data.clicks}</strong></div>
        <div className="dash-kpi dash-kpi-amber"><span>Conversão</span><strong>{data.conversionPercent === null ? "—" : `${data.conversionPercent.replace(".", ",")}%`}</strong></div>
      </div>}
    </Bloco>

    <SaldoBuckets block={dashboard.balance} />

    <Bloco className="dash-main" title="Produtos que mais rendem" block={dashboard.topProducts}
      emptyTitle="Nenhuma venda indicada no período" emptyDescription="Os produtos que mais geraram comissão aparecem aqui.">
      {items => <div className="dash-table-wrap"><Tabela caption="Produtos que mais rendem" headers={["Produto", "Vendas", "Comissão"]} rows={items.map((item): ReactNode[] => [
        item.productName,
        item.sales,
        <span key={`${item.productId}-c`} className="paysi-valor">{formatarCentavos(item.commissionCents)}</span>,
      ])} /></div>}
    </Bloco>

    <Bloco className="dash-side" title="Suas afiliações" block={dashboard.affiliations}
      emptyTitle="Você ainda não é afiliado de nenhum produto" emptyDescription={<>Veja os produtos disponíveis na <Link href="/vitrine">vitrine</Link> e peça afiliação.</>}>
      {data => <div>
        <ul className="dash-list">
          <li><span>Ativas</span><strong>{data.active}</strong></li>
          <li><span>Aguardando aprovação</span><strong>{data.pending}</strong></li>
        </ul>
        <p className="dash-links"><Link href="/meus-links">Ver meus links</Link> · <Link href="/vitrine">Encontrar produtos</Link></p>
      </div>}
    </Bloco>

    <Bloco className="dash-main" title="Últimas comissões" block={dashboard.recentCommissions}
      emptyTitle="Nenhuma comissão recente" emptyDescription="As comissões das vendas que você indicou aparecerão aqui.">
      {items => <div className="dash-table-wrap"><Tabela caption="Últimas comissões" headers={["Produto", "Comissão", "Status", "Data"]} rows={items.map((item): ReactNode[] => [
        item.productName,
        <span key={`${item.id}-c`} className="paysi-valor">{formatarCentavos(item.commissionCents)}</span>,
        commissionStatusLabel[item.status] ?? item.status,
        formatDate(item.occurredAt),
      ])} /></div>}
    </Bloco>

    <ProximosRecebimentos block={dashboard.nextReceivables} title="Próximas liberações" emptyDescription="As comissões a caminho do seu saldo disponível aparecem aqui." />
  </div>;
}
