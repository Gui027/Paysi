"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { currentSession, SessionCreated, switchMode } from "../lib/sessao";
import { Botao } from "./ui";
import { LogoutButton } from "./LogoutButton";

const links = [["/inicio", "Início"], ["/produtos", "Produtos"], ["/vendas", "Vendas"], ["/assinaturas", "Assinaturas"], ["/afiliados", "Afiliados"], ["/saldo", "Saldo"], ["/componentes", "Componentes"]] as const;

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const [open, setOpen] = useState(false);
  const [mode, setMode] = useState<SessionCreated["activeMode"]>("SELLER");
  const [changing, setChanging] = useState(false);

  useEffect(() => { currentSession().then(session => setMode(session.activeMode)).catch(() => undefined); }, []);
  async function changeMode(nextMode: SessionCreated["activeMode"]) {
    if (nextMode === mode || changing) return;
    setChanging(true);
    try { const session = await switchMode(nextMode); setMode(session.activeMode); } finally { setChanging(false); }
  }

  return <div className="app-shell">
    <a className="skip-link" href="#conteudo">Ir para o conteúdo</a>
    <aside id="menu-principal" className={`sidebar ${open ? "sidebar-open" : ""}`}>
      <img src="/paysi-logo.svg" alt="Paysi" />
      <nav aria-label="Navegação principal">{links.map(([href, label]) => <Link key={href} href={href} aria-current={pathname === href || pathname.startsWith(`${href}/`) ? "page" : undefined} onClick={() => setOpen(false)}>{label}</Link>)}</nav>
      <LogoutButton />
    </aside>
    <div className="app-column">
      <header className="app-header">
        <Botao variant="secondary" className="menu-button" aria-expanded={open} aria-controls="menu-principal" onClick={() => setOpen(value => !value)}>Menu</Botao>
        <div className="mode-switch" role="group" aria-label="Modo ativo">
          <button aria-pressed={mode === "SELLER"} disabled={changing} onClick={() => void changeMode("SELLER")}>Vender</button>
          <button aria-pressed={mode === "AFFILIATE"} disabled={changing} onClick={() => void changeMode("AFFILIATE")}>Divulgar</button>
        </div>
      </header>
      <main className="content" id="conteudo" tabIndex={-1}>{children}</main>
    </div>
  </div>;
}
