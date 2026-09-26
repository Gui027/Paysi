"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { currentSession, SessionCreated, switchMode } from "../lib/sessao";
import { icons } from "./Icons";
import { LogoutButton } from "./LogoutButton";
import "./shell.css";

type NavItem = readonly [href: string, label: string, icon: keyof typeof icons, children?: readonly NavItem[]];

// Menu enxuto por modo. As rotas que saíram (perfil, plano, componentes, cupons) continuam
// existindo — só não ocupam o menu. Cupons abre pelo botão na lista de Produtos.
const sellerLinks: readonly NavItem[] = [
  ["/inicio", "Dashboard", "inicio"], ["/produtos", "Produtos", "produtos"], ["/vendas", "Vendas", "vendas", [["/vendas/reembolsos", "Reembolsos", "reembolsos"]]],
  ["/assinaturas", "Assinaturas", "assinaturas"], ["/afiliados", "Afiliados", "afiliados"],
  ["/saldo", "Financeiro", "financeiro"], ["/relatorios", "Relatórios", "relatorios"], ["/colaboradores", "Colaboradores", "colaboradores"], ["/apps", "Apps", "apps"],
];
const affiliateLinks: readonly NavItem[] = [
  ["/inicio", "Dashboard", "inicio"], ["/vitrine", "Marketplace", "vitrine"], ["/meus-links", "Meus links", "links"],
  ["/saldo", "Financeiro", "financeiro"],
];

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const [mode, setMode] = useState<SessionCreated["activeMode"]>("SELLER");
  const [changing, setChanging] = useState(false);
  const [drawer, setDrawer] = useState(false);

  useEffect(() => { currentSession().then(session => setMode(session.activeMode)).catch(() => undefined); }, []);
  useEffect(() => { setDrawer(false); }, [pathname]);

  async function changeMode(nextMode: SessionCreated["activeMode"]) {
    if (nextMode === mode || changing) return;
    setChanging(true);
    try { const session = await switchMode(nextMode); setMode(session.activeMode); } finally { setChanging(false); }
  }

  const items = mode === "AFFILIATE" ? affiliateLinks : sellerLinks;

  return <div className="shell" data-drawer={drawer ? "open" : "closed"}>
    <a className="skip-link" href="#conteudo">Ir para o conteúdo</a>
    <header className="shell-top">
      <button type="button" className="shell-burger" aria-label={drawer ? "Fechar menu" : "Abrir menu"} aria-expanded={drawer}
        aria-controls="shell-nav" onClick={() => setDrawer(open => !open)}>{drawer ? icons.fechar : icons.menu}</button>
      <Link href="/inicio" className="shell-brand"><img src="/paysi-logo-negativo.svg" alt="Paysi" /></Link>
      <div className="shell-top-spacer" />
      <div className="shell-mode" role="group" aria-label="Modo ativo">
        <button aria-pressed={mode === "SELLER"} disabled={changing} onClick={() => void changeMode("SELLER")}>Vender</button>
        <button aria-pressed={mode === "AFFILIATE"} disabled={changing} onClick={() => void changeMode("AFFILIATE")}>Divulgar</button>
      </div>
      <details className="shell-user">
        <summary aria-label="Menu da conta"><span className="shell-avatar">{icons.usuario}</span><span className="shell-caret">{icons.seta}</span></summary>
        <div className="shell-user-menu">
          <Link href="/saldo?aba=identidade">Verificação de identidade</Link>
          <LogoutButton />
        </div>
      </details>
    </header>
    <aside className="shell-side" id="shell-nav">
      <nav aria-label="Navegação principal">{items.map(([href, label, icon, children]) => {
        const inside = pathname === href || pathname.startsWith(`${href}/`);
        const childActive = children?.some(([childHref]) => pathname === childHref || pathname.startsWith(`${childHref}/`)) ?? false;
        return <div key={href} className="shell-group">
          <Link href={href} aria-current={inside && !childActive ? "page" : undefined}>
            {icons[icon]}<span>{label}</span>
            {children && <span className="shell-chevron" aria-hidden="true">{inside ? "⌃" : "⌄"}</span>}
          </Link>
          {children && inside && children.map(([childHref, childLabel, childIcon]) =>
            <Link key={childHref} href={childHref} className="shell-sub" aria-current={pathname === childHref || pathname.startsWith(`${childHref}/`) ? "page" : undefined}>
              {icons[childIcon]}<span>{childLabel}</span>
            </Link>)}
        </div>;
      })}
        <div className="shell-group">
          <a href="/ajuda" target="_blank" rel="noopener noreferrer">{icons.ajuda}<span>Ajuda</span><span className="sr-only"> (abre em uma nova guia)</span></a>
        </div>
      </nav>
    </aside>
    <button type="button" className="shell-scrim" aria-label="Fechar menu" tabIndex={-1} onClick={() => setDrawer(false)} />
    <main className="shell-main content" id="conteudo" tabIndex={-1}>{children}</main>
  </div>;
}
