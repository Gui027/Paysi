"use client";

import { motion, useScroll } from "framer-motion";
import { useRef } from "react";
import { IconCard, IconCursorClick, IconLayers, IconChartUp, IconZap } from "./icons";
import { Reveal } from "./Reveal";

const items = [
  {
    icon: <IconZap size={20} />,
    title: "Checkout 52% mais rápido",
    text: "Na primeira compra, nosso checkout tem uma experiência 52% mais rápida do que um site normal. O resultado? Um aumento de conversão entre 20% e 50% nas vendas.",
  },
  {
    icon: <IconCursorClick size={20} />,
    title: "Compras com 1-click e sem fricção",
    text: "Após a primeira compra, salvamos os dados do cliente com segurança para que ele compre com 1-click em toda a sua loja, sempre que quiser.",
  },
  {
    icon: <IconCard size={20} />,
    title: "Controle total de pagamentos",
    text: "Integramos com os melhores meios de pagamento e antifraude do mercado. Escolha o seu favorito e otimize sua taxa de aprovação.",
  },
  {
    icon: <IconLayers size={20} />,
    title: "Customize suas preferências",
    text: "Mantenha sua logo e cores no checkout. Impulsione sua experiência e aumente a fidelidade dos seus clientes.",
  },
  {
    icon: <IconChartUp size={20} />,
    title: "Insights de consumo",
    text: "Acompanhe em tempo real os resultados dos testes A/B para aumentar a conversão em cada etapa da jornada de compra.",
  },
];

export function Timeline() {
  const ref = useRef<HTMLDivElement>(null);
  const { scrollYProgress } = useScroll({ target: ref, offset: ["start 0.8", "end 0.4"] });

  return (
    <section className="timeline-section">
      <div className="site-container">
        <Reveal className="section-head-center">
          <span className="section-eyebrow-pill">Mais clientes</span>
          <h2 className="section-title section-title-center">
            Entenda como a Paysi aumenta a sua conversão
          </h2>
        </Reveal>

        <div className="timeline" ref={ref}>
          <div className="timeline-line">
            <motion.div className="timeline-line-fill" style={{ scaleY: scrollYProgress, height: "100%" }} />
          </div>

          {items.map((item, index) => (
            <div className="timeline-row" key={item.title}>
              <Reveal className="timeline-visual" delay={0.05}>
                <div className="timeline-card" style={{ marginInline: index % 2 === 0 ? "auto 0" : "0 auto" }}>
                  <span className="icon-badge">{item.icon}</span>
                  <span className="title">{item.title}</span>
                </div>
              </Reveal>
              <div className="timeline-dot">{item.icon}</div>
              <Reveal className="timeline-copy" delay={0.1}>
                <h3>{item.title}</h3>
                <p>{item.text}</p>
              </Reveal>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
