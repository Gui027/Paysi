import { ReactNode } from "react";

// Ícones de traço simples (24x24), desenhados para a Paysi — herdam a cor do texto (currentColor).
function Icon({ children }: { children: ReactNode }) {
  return <svg className="shell-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
    strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">{children}</svg>;
}

export const icons = {
  inicio: <Icon><path d="M3 11.5 12 4l9 7.5" /><path d="M5.5 10v9.5h13V10" /><path d="M10 19.5v-5h4v5" /></Icon>,
  produtos: <Icon><path d="M3.5 12.5V4.5h8l9 9-8 8-9-9Z" /><circle cx="8" cy="9" r="1.3" /></Icon>,
  vendas: <Icon><path d="M3 17 9 11l4 4 8-9" /><path d="M15 6h6v6" /></Icon>,
  assinaturas: <Icon><path d="M4 12a8 8 0 0 1 13.5-5.8L20 8.5" /><path d="M20 4v4.5h-4.5" /><path d="M20 12a8 8 0 0 1-13.5 5.8L4 15.5" /><path d="M4 20v-4.5h4.5" /></Icon>,
  afiliados: <Icon><circle cx="9" cy="8.5" r="3.2" /><path d="M3 20c.4-3.4 2.8-5.5 6-5.5s5.6 2.1 6 5.5" /><circle cx="17.5" cy="9.5" r="2.5" /><path d="M17 14.6c2.3.2 3.7 1.7 4 4.4" /></Icon>,
  financeiro: <Icon><rect x="3" y="6" width="18" height="13" rx="2.5" /><path d="M3 10.5h18" /><path d="M7 15h3" /></Icon>,
  integracoes: <Icon><path d="M9 3v5M15 3v5" /><path d="M6.5 8h11v3.5a5.5 5.5 0 0 1-11 0V8Z" /><path d="M12 17v4" /></Icon>,
  verificacao: <Icon><path d="M12 3 5 6v5.5c0 4.3 2.9 7.7 7 9.5 4.1-1.8 7-5.2 7-9.5V6l-7-3Z" /><path d="m9 12 2.2 2.2L15.5 10" /></Icon>,
  vitrine: <Icon><path d="M4 9h16l-1.2-4.5H5.2L4 9Z" /><path d="M5 9v10.5h14V9" /><path d="M10 19.5v-5h4v5" /></Icon>,
  links: <Icon><path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1" /><path d="M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1" /></Icon>,
  reembolsos: <Icon><path d="M4 12a8 8 0 1 0 2.5-5.8" /><path d="M4 4v4.5h4.5" /><path d="M12 8v8M9.5 10.5c0-1 1-1.6 2.5-1.6s2.5.7 2.5 1.6-1 1.4-2.5 1.6-2.5.7-2.5 1.6 1 1.6 2.5 1.6 2.5-.6 2.5-1.6" /></Icon>,
  relatorios: <Icon><path d="M5 20V11M12 20V4M19 20v-7" /></Icon>,
  colaboradores: <Icon><circle cx="9" cy="8" r="3" /><path d="M3.500 19c.5-3.400 2.600-5 5.500-5s5 1.600 5.500 5" /><circle cx="17" cy="9" r="2.300" /><path d="M16 14c2.500 0 4 1.400 4.500 4" /></Icon>,
  menu: <Icon><path d="M4 7h16M4 12h16M4 17h16" /></Icon>,
  fechar: <Icon><path d="M6 6l12 12M18 6 6 18" /></Icon>,
  usuario: <Icon><circle cx="12" cy="8.5" r="3.6" /><path d="M4.5 20.5c.6-4 3.4-6.2 7.5-6.2s6.9 2.2 7.5 6.2" /></Icon>,
  seta: <Icon><path d="m6 9 6 6 6-6" /></Icon>,
} as const;
