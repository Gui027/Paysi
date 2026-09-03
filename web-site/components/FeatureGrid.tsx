import { IconShield, IconLayers, IconChartUp, IconLock } from "./icons";
import { StaggerGroup, StaggerItem } from "./Reveal";

const features = [
  {
    icon: <IconShield size={22} />,
    title: "Solução segura",
    text: "Seguimos os padrões de proteção de dados de cartão (PCI-DSS) e a LGPD.",
  },
  {
    icon: <IconLayers size={22} />,
    title: "Integração rápida",
    text: "Integre o checkout Paysi à sua loja via plugin ou API, em poucos passos.",
  },
  {
    icon: <IconChartUp size={22} />,
    title: "Leitura de dados",
    text: "Receba insights sobre hábitos de consumo e personalize suas ofertas.",
  },
  {
    icon: <IconLock size={22} />,
    title: "Sem cadastros e senhas",
    text: "Traga a melhor experiência de compra para o seu cliente, sem fricção.",
  },
];

export function FeatureGrid() {
  return (
    <section className="features-section">
      <div className="site-container">
        <StaggerGroup className="features-grid">
          {features.map((feature) => (
            <StaggerItem key={feature.title} className="feature-card">
              <span className="icon-badge">{feature.icon}</span>
              <h3>{feature.title}</h3>
              <p>{feature.text}</p>
            </StaggerItem>
          ))}
        </StaggerGroup>
      </div>
    </section>
  );
}
