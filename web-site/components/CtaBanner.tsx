import { Reveal } from "./Reveal";

export function CtaBanner() {
  return (
    <section id="contato" style={{ padding: "0 0 clamp(40px, 4.5vw, 70px)" }}>
      <Reveal>
        <div className="cta-banner">
          <div className="cta-banner-glow" />
          <img className="site-logo" src="/paysi-logo.svg" alt="Paysi" />
          <h2>Comece a usar a Paysi hoje mesmo</h2>
          <p>Quer melhorar a experiência de compra do seu e-commerce? Fale com nosso time comercial.</p>
          <a className="btn-pill-avatar" href="mailto:comercial@paysi.com.br">
            <span className="avatar">P</span>
            Fale com nossa equipe
          </a>
        </div>
      </Reveal>
    </section>
  );
}
