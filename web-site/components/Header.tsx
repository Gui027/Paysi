"use client";

import { useEffect, useState } from "react";
import { IconUser } from "./icons";

const PAINEL_BASE_URL = (process.env.NEXT_PUBLIC_PAINEL_BASE_URL ?? "https://painel.paysi.com.br").replace(/\/$/, "");

export function Header() {
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 8);
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  return (
    <header className="site-header" data-scrolled={scrolled}>
      <div className="site-container site-header-row">
        <img className="site-logo" src="/paysi-logo.svg" alt="Paysi" />
        <div className="site-header-actions">
          <a className="site-nav-link" href="#recursos">
            <IconUser size={18} />
            Sou lojista
          </a>
          <a className="site-nav-link" href={`${PAINEL_BASE_URL}/entrar`}>
            Entrar
          </a>
          <a className="btn btn-contorno" href={`${PAINEL_BASE_URL}/criar-conta`}>
            Criar conta
          </a>
          <a className="btn btn-primaria" href="#contato">
            Entre em contato
          </a>
        </div>
      </div>
    </header>
  );
}
