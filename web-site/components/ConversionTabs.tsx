"use client";

import { AnimatePresence, motion } from "framer-motion";
import { useState } from "react";
import { IconChevron, IconUser, IconWallet } from "./icons";
import { Reveal } from "./Reveal";

const slides = [
  {
    icon: <IconUser size={22} />,
    title: "Seus dados",
    text: "Na primeira etapa do checkout, pedimos apenas os dados necessários para fazer um pedido online.",
  },
  {
    icon: <IconWallet size={22} />,
    title: "Formas de pagamento",
    text: "Pix, cartão e boleto no mesmo checkout, com antifraude embutido e taxa de aprovação otimizada.",
  },
];

export function ConversionTabs() {
  const [tab, setTab] = useState<0 | 1>(0);
  const [slide, setSlide] = useState(0);

  return (
    <section className="tabs-section">
      <div className="site-container">
        <Reveal className="section-head-center">
          <span className="section-eyebrow-pill">Como funciona</span>
          <h2 className="section-title section-title-center">
            Aumente sua conversão e receita com a Paysi
          </h2>
        </Reveal>

        <div className="tabs-grid">
          <Reveal>
            <div className="tab-switch">
              <button type="button" data-active={tab === 0} onClick={() => setTab(0)}>
                Checkout da 1ª compra
                {tab === 0 && <motion.span layoutId="tab-indicator" className="indicator" style={{ left: 0, right: 0 }} />}
              </button>
              <button type="button" data-active={tab === 1} onClick={() => setTab(1)}>
                1-Click Checkout
                {tab === 1 && <motion.span layoutId="tab-indicator" className="indicator" style={{ left: 0, right: 0 }} />}
              </button>
            </div>

            <div className="tab-feature-list">
              <AnimatePresence mode="wait">
                <motion.div
                  key={`${tab}-${slide}`}
                  initial={{ opacity: 0, y: 12 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -12 }}
                  transition={{ duration: 0.35 }}
                  className="tab-feature"
                >
                  <span className="icon-badge">{slides[slide].icon}</span>
                  <div>
                    <h3>{slides[slide].title}</h3>
                    <p>{slides[slide].text}</p>
                  </div>
                </motion.div>
              </AnimatePresence>
            </div>

            <div className="tab-pagination">
              <button className="arrow" type="button" aria-label="Anterior" onClick={() => setSlide((s) => (s === 0 ? slides.length - 1 : s - 1))}>
                <IconChevron size={16} direction="left" />
              </button>
              <div className="dots">
                {slides.map((_, i) => (
                  <button
                    key={i}
                    className="dot"
                    data-active={slide === i}
                    aria-label={`Slide ${i + 1}`}
                    onClick={() => setSlide(i)}
                  />
                ))}
              </div>
              <button className="arrow" type="button" aria-label="Próximo" onClick={() => setSlide((s) => (s + 1) % slides.length)}>
                <IconChevron size={16} direction="right" />
              </button>
            </div>
          </Reveal>

          <Reveal delay={0.15} className="tab-visual">
            <div className="tab-visual-backdrop" />
            <div className="phone-mockup" style={{ position: "relative" }}>
              <div className="phone-mockup-notch" />
              <div className="phone-mockup-screen">
                <div className="phone-row">
                  <span className="phone-chip">
                    <span className="phone-chip-dot" />
                    Sua marca
                  </span>
                  <span style={{ font: "700 0.8125rem/1 var(--fonte-marca)" }}>R$ 670,00</span>
                </div>
                <div className="phone-tabs">
                  <span className={tab === 0 ? "active" : undefined}>Dados</span>
                  <span>Entrega</span>
                  <span className={tab === 1 ? "active" : undefined}>Pagamento</span>
                </div>
                <div>
                  <span className="phone-field-label">CPF</span>
                  <div className="phone-field" />
                </div>
                <div className="phone-field-row">
                  <div>
                    <span className="phone-field-label">CEP</span>
                    <div className="phone-field" />
                  </div>
                  <div>
                    <span className="phone-field-label">Número</span>
                    <div className="phone-field" />
                  </div>
                </div>
                <div>
                  <span className="phone-field-label">Endereço</span>
                  <div className="phone-field" />
                </div>
                <button className="phone-cta" type="button">
                  Continuar
                </button>
              </div>
            </div>
          </Reveal>
        </div>
      </div>
    </section>
  );
}
