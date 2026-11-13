import { CheckoutContract, cycleLabel } from "../lib/checkout";
import { formatarCentavos } from "../lib/formato";

const dataFormatada = new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "long", year: "numeric" });

export function ResumoDaOferta({ contract }: { contract: CheckoutContract }) {
  const { appearance } = contract;
  return (
    <header className="offer-summary">
      {appearance.bannerUrl && <img className="offer-banner" src={appearance.bannerUrl} alt="" />}
      <div className="offer-product">
        <img className="merchant-mark" src={appearance.logoUrl ?? "/paysi-logo.svg"} alt="" />
        <div className="offer-copy">
          <h1>{contract.product}</h1>
          <div className="price paysi-valor" style={{ color: appearance.primaryColor }}>{contract.pricingMode === "CUSTOMER_DEFINED" ? "Você decide o valor" : formatarCentavos(contract.priceCents)}</div>
          <div className="offer-meta">
            {contract.pricingMode === "CUSTOMER_DEFINED" && <span>Valor mínimo: {formatarCentavos(contract.priceCents)}</span>}
            {contract.cycle && <span>Cobrança {cycleLabel[contract.cycle]}</span>}
            {contract.nextChargeAt && <span>Próxima cobrança em {dataFormatada.format(new Date(contract.nextChargeAt))}</span>}
          </div>
        </div>
      </div>
    </header>
  );
}
