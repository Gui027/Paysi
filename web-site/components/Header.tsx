"use client";

import { useEffect, useState } from "react";
import { PAINEL_BASE_URL } from "@/lib/urls";
import { IconUser } from "./icons";

export function Header({ base = "" }: { base?: string }) {
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
        <a href={base || "/"} aria-label="Paysi — página inicial"><img className="site-logo" src="/paysi-logo.svg" alt="Paysi" /></a>
        <div className="site-header-actions">
          <a className="site-nav-link" href={`${base}#recursos`}>
            <IconUser size={18} />
            Sou lojista
          </a>
          <a className="site-nav-link" href={`${PAINEL_BASE_URL}/entrar`}>
            Entrar
          </a>
          <a className="btn btn-contorno" href={`${PAINEL_BASE_URL}/criar-conta`}>
            Criar conta
          </a>
          <a className="btn btn-primaria" href={`${base}#contato`}>
            Entre em contato
          </a>
        </div>
      </div>
    </header>
  );
}
