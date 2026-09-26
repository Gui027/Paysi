export type HelpCategory = { slug: string; title: string; description: string };

/** Categorias da Central de Ajuda. Os artigos de cada uma entram depois; por enquanto só a casca. */
export const helpCategories: readonly HelpCategory[] = [
  { slug: "comprei-um-produto", title: "Comprei um produto", description: "Tem um problema na sua compra? Podemos te ajudar!" },
  { slug: "produtos", title: "Produtos", description: "Como cadastrar e gerenciar seus produtos." },
  { slug: "financeiro", title: "Financeiro", description: "Tudo sobre pagamentos, taxas, saques, extrato." },
  { slug: "sobre-a-paysi", title: "Sobre a Paysi", description: "Saiba mais sobre a nossa plataforma de infoprodutos." },
  { slug: "area-de-membros", title: "Área de membros", description: "Crie e configure a sua área de membros grátis." },
  { slug: "perguntas-frequentes", title: "Perguntas frequentes", description: "Tire suas dúvidas e conheça as perguntas mais frequentes." },
  { slug: "integracoes", title: "Integrações", description: "Conecte com ferramentas de e-mail marketing, nota fiscal, entre outros." },
  { slug: "configuracoes", title: "Configurações", description: "Ajustes gerais da plataforma." },
  { slug: "afiliados", title: "Afiliados", description: "Venda o seu produto através de afiliados, ou afilie-se a produtos de outras pessoas." },
];

function plain(text: string): string {
  return text.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();
}

/** Filtra as categorias pelo texto digitado, sem diferenciar maiúsculas nem acentos. */
export function filterCategories(query: string): readonly HelpCategory[] {
  const term = plain(query.trim());
  if (!term) return helpCategories;
  return helpCategories.filter(category => plain(`${category.title} ${category.description}`).includes(term));
}

export function findCategory(slug: string): HelpCategory | undefined {
  return helpCategories.find(category => category.slug === slug);
}
