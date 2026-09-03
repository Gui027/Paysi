import { Reveal } from "./Reveal";

const platforms = ["VTEX", "Shopify", "WooCommerce", "Nuvemshop", "Magento", "Loja Integrada"];

export function LogosRow() {
  return (
    <section className="logos-section">
      <div className="site-container">
        <Reveal>
          <p className="logos-eyebrow">Pronto para integrar com as principais plataformas</p>
        </Reveal>
        <Reveal delay={0.1}>
          <div className="logos-row">
            {platforms.map((name) => (
              <span key={name} className="logo-wordmark">
                {name}
              </span>
            ))}
          </div>
        </Reveal>
      </div>
    </section>
  );
}
