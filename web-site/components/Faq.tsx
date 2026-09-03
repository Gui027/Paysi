import { IconLayers, IconChartUp, IconShield, IconZap } from "./icons";
import { StaggerGroup, StaggerItem, Reveal } from "./Reveal";
import { ContactBanner } from "./ContactBanner";

const faqs = [
  {
    icon: <IconLayers size={20} />,
    q: "Como funciona a Paysi?",
    a: "Após a 1ª compra em nosso checkout, seu cliente passa a comprar em apenas 1-click em toda a sua loja. O resultado é um aumento expressivo de conversão para o seu negócio.",
  },
  {
    icon: <IconChartUp size={20} />,
    q: "Vou vender mais com a Paysi?",
    a: "Sim! Nosso checkout tem uma experiência de compra 52% mais rápida do que um site normal, o que gera um aumento de conversão entre 20% e 50% maior nas vendas.",
  },
  {
    icon: <IconShield size={20} />,
    q: "É seguro? E a LGPD?",
    a: "Nossa solução segue os mais altos padrões internacionais de segurança (PCI-DSS) e toda a legislação vigente de privacidade e proteção aos dados do consumidor (LGPD).",
  },
  {
    icon: <IconZap size={20} />,
    q: "É fácil de instalar?",
    a: "Sim, a instalação é rápida e sem complicação. Integramos com as principais plataformas de e-commerce do mercado.",
  },
];

export function Faq() {
  return (
    <section className="faq-section">
      <div className="site-container">
        <Reveal className="section-head-center">
          <span className="section-eyebrow-pill">Perguntas frequentes</span>
          <h2 className="section-title section-title-center">
            Quer comprar, mas ficou na dúvida? A Paysi responde
          </h2>
        </Reveal>

        <StaggerGroup className="faq-list">
          {faqs.map((faq) => (
            <StaggerItem key={faq.q} className="faq-item">
              <span className="icon-badge">{faq.icon}</span>
              <h3>{faq.q}</h3>
              <p>{faq.a}</p>
            </StaggerItem>
          ))}
        </StaggerGroup>

        <ContactBanner />
      </div>
    </section>
  );
}
