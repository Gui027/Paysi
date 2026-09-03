import { IconUser, IconLayers, IconChartUp } from "./icons";
import { StaggerGroup, StaggerItem, Reveal } from "./Reveal";

const steps = [
  {
    number: "01",
    icon: <IconUser size={26} />,
    title: "Checkout rápido",
    text: "Após a primeira compra, seu cliente compra com 1-click para sempre.",
  },
  {
    number: "02",
    icon: <IconLayers size={26} />,
    title: "Efeito multiloja",
    text: "O checkout reconhece o cliente em toda a sua rede de lojas online.",
  },
  {
    number: "03",
    icon: <IconChartUp size={26} />,
    title: "Todo mundo ganha",
    text: "Tudo pronto! Agora é só aproveitar o aumento de conversão.",
  },
];

export function MagicSteps() {
  return (
    <section className="steps-section">
      <div className="site-container">
        <Reveal>
          <span className="section-eyebrow-pill">Efeito de rede</span>
          <h2 className="section-title">Compras mágicas com apenas 1-click</h2>
        </Reveal>

        <StaggerGroup className="steps-grid">
          {steps.map((step) => (
            <StaggerItem key={step.number} className="step-card">
              <div className="step-visual">
                <span className="icon-badge" style={{ width: 48, height: 48 }}>
                  {step.icon}
                </span>
                <span className="step-number">{step.number}</span>
              </div>
              <h3>{step.title}</h3>
              <p>{step.text}</p>
            </StaggerItem>
          ))}
        </StaggerGroup>
      </div>
    </section>
  );
}
