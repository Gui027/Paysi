import { formatarCentavos } from "../../../lib/moeda";
import { ReportChart } from "../../../lib/relatorios";

/** Gráfico de barras: eixo e alturas já vêm calculados do backend; aqui só se desenha. */
export function GraficoBarras({ chart, label, firstName, secondName }: { chart: ReportChart; label: string; firstName?: string; secondName?: string }) {
  return <figure className="rel-chart" aria-label={label}>
    {secondName && <figcaption className="rel-legend"><span><i className="rel-dot rel-dot-a" />{firstName}</span><span><i className="rel-dot rel-dot-b" />{secondName}</span></figcaption>}
    <div className="rel-plot">
      <div className="rel-axis" aria-hidden="true">
        {chart.ticks.map(tick => <span key={tick.cents} style={{ bottom: `${tick.bottomPercent}%` }}>{formatarCentavos(tick.cents)}</span>)}
      </div>
      <div className="rel-bars">
        {chart.ticks.map(tick => <i key={tick.cents} className="rel-grid" style={{ bottom: `${tick.bottomPercent}%` }} aria-hidden="true" />)}
        {chart.points.map(point => <div className="rel-col" key={point.label}>
          <div className="rel-pair">
            <span className="rel-bar" style={{ height: `${point.heightPercent}%` }} title={`${point.label}: ${formatarCentavos(point.valueCents)}`} />
            {secondName && <span className="rel-bar rel-bar-b" style={{ height: `${point.secondPercent}%` }} title={`${point.label}: ${formatarCentavos(point.secondCents)}`} />}
          </div>
          <span className="rel-x">{point.label}</span>
        </div>)}
      </div>
    </div>
  </figure>;
}
