"use client";

import { motion, useScroll, useTransform } from "framer-motion";
import { useRef } from "react";
import { IconChartUp, IconCheckBadge, IconPlay } from "./icons";

export function Hero() {
  const ref = useRef<HTMLDivElement>(null);
  const { scrollYProgress } = useScroll({ target: ref, offset: ["start start", "end start"] });
  const y1 = useTransform(scrollYProgress, [0, 1], [0, -60]);
  const y2 = useTransform(scrollYProgress, [0, 1], [0, 40]);

  return (
    <section className="hero" ref={ref}>
      <div className="hero-glow" />
      <div className="site-container">
        <div className="hero-grid">
          <motion.div
            initial={{ opacity: 0, y: 24 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.7, ease: [0.2, 0, 0.2, 1] }}
          >
            <span className="hero-eyebrow">Checkout Paysi</span>
            <h1 className="hero-title">
              Checkout de <em>alta conversão</em>
            </h1>
            <p className="hero-subtitle">
              Substitua o checkout padrão do seu site pela Paysi: pagamentos mais rápidos, menos
              abandono de carrinho e mais vendas fechadas.
            </p>
            <div className="hero-actions">
              <a className="btn btn-primaria" href="#contato">
                Fale com nossa equipe
              </a>
              <button className="btn-play" type="button">
                <span className="play-circle">
                  <IconPlay size={16} />
                </span>
                Assista ao vídeo
              </button>
            </div>
          </motion.div>

          <div className="hero-visual">
            <div className="hero-visual-inner">
              <motion.div
                initial={{ opacity: 0, scale: 0.92 }}
                animate={{ opacity: 1, scale: 1 }}
                transition={{ duration: 0.8, delay: 0.15, ease: [0.2, 0, 0.2, 1] }}
                style={{ position: "relative" }}
              >
                <div className="phone-mockup">
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
                      <span className="active">Dados</span>
                      <span>Entrega</span>
                      <span>Pagamento</span>
                    </div>
                    <div>
                      <span className="phone-field-label">E-mail</span>
                      <div className="phone-field" />
                    </div>
                    <div className="phone-field-row">
                      <div>
                        <span className="phone-field-label">Nome completo</span>
                        <div className="phone-field" />
                      </div>
                      <div>
                        <span className="phone-field-label">Celular</span>
                        <div className="phone-field" />
                      </div>
                    </div>
                    <div>
                      <span className="phone-field-label">Endereço de entrega</span>
                      <div className="phone-field" />
                    </div>
                    <button className="phone-cta" type="button">
                      Continuar
                    </button>
                  </div>
                </div>

                <motion.div className="float-card float-card-metric" style={{ y: y1 }}>
                  <div className="icon-bump">
                    <IconChartUp size={16} />
                  </div>
                  <span style={{ font: "var(--texto-apoio-p)", color: "var(--texto-apoio)" }}>
                    Aumento de vendas
                  </span>
                  <div className="value">37,24%</div>
                  <div className="delta">
                    <IconChartUp size={13} />
                    +7,24% última semana
                  </div>
                </motion.div>

                <motion.div className="float-card float-card-product" style={{ y: y2 }}>
                  <span className="check">
                    <IconCheckBadge size={16} />
                  </span>
                  <div className="thumb" />
                  <div>
                    <div className="name">Assinatura Pro</div>
                    <div className="price">R$ 249</div>
                  </div>
                </motion.div>
              </motion.div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
