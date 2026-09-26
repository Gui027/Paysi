const glyphs: Record<string, React.ReactNode> = {
  "comprei-um-produto": <><path d="M2 9.500 12 5l10 4.500-10 4.500L2 9.500Z" /><path d="M6 11.500V16c0 1.500 2.700 3 6 3s6-1.500 6-3v-4.500" /></>,
  produtos: <><path d="M4 8.500 12 4l8 4.500v9L12 22l-8-4.500v-9Z" /><path d="M4 8.500 12 13l8-4.500M12 13v9" /></>,
  financeiro: <><rect x="3" y="6" width="18" height="12" rx="2" /><circle cx="12" cy="12" r="2.500" /><path d="M6 9v.01M18 15v.01" /></>,
  "sobre-a-paysi": <><circle cx="12" cy="12" r="9" /><path d="M12 11v5M12 8v.01" /></>,
  "area-de-membros": <><rect x="3" y="4" width="18" height="13" rx="2" /><path d="m10 8.500 4.500 2.500-4.500 2.500v-5ZM8 21h8M12 17v4" /></>,
  "perguntas-frequentes": <><path d="M9 18h6M10 21h4" /><path d="M12 3a6 6 0 0 0-3.500 10.900c.6.5 1 1.200 1 2.100h5c0-.9.4-1.600 1-2.100A6 6 0 0 0 12 3Z" /></>,
  integracoes: <><path d="M9 7V3M15 7V3M6 7h12v4a6 6 0 0 1-12 0V7Z" /><path d="M12 17v4" /></>,
  configuracoes: <><circle cx="12" cy="12" r="3" /><path d="M12 2v3M12 19v3M4.900 4.900 7 7M17 17l2.100 2.100M2 12h3M19 12h3M4.900 19.100 7 17M17 7l2.100-2.100" /></>,
  afiliados: <><circle cx="12" cy="8" r="3.500" /><path d="M5 20c.6-4 3.200-6 7-6s6.400 2 7 6" /><path d="M18 3v4M16 5h4" /></>,
};

export function CategoriaIcone({ slug }: { slug: string }) {
  return <span className="aj-icon"><svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.500" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">{glyphs[slug]}</svg></span>;
}
