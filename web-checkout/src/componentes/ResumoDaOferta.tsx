import { CheckoutContract, cycleLabel } from "../lib/checkout";
import { formatarCentavos } from "../lib/formato";

const dataFormatada = new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "long", year: "numeric" });

export function ResumoDaOferta({ contract }: { contract: CheckoutContract }) {
  const { appearance } = contract;
  return (
    <aside className="offer-summary" style={{ background: `linear-gradient(160deg, ${appearance.primaryColor}, var(--blue-600))` }}>
      <img className="merchant-mark" src={appearance.logoUrl ?? "/paysi-logo.svg"} alt="" />
      <span className="eyebrow">Você está comprando</span>
      <h1>{contract.product}</h1>
      <div className="price paysi-valor">{formatarCentavos(contract.priceCents)}</div>
      {contract.cycle && <small>Cobrança {cycleLabel[contract.cycle]}</small>}
      <div className="summary-dates">
        <span>Hoje: {dataFormatada.format(new Date(contract.today))}</span>
        {contract.nextChargeAt && <span>Próxima cobrança: {dataFormatada.format(new Date(contract.nextChargeAt))}</span>}
      </div>
    </aside>
  );
}
