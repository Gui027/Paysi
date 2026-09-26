import { paginasVisiveis } from "../lib/vendas";

/** Paginação numerada (1 2 3 … 33) com "Exibindo X de Y páginas", como nas listas da Kiwify. */
export function Paginacao({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (page: number) => void }) {
  return <div className="pg">
    <span className="pg-info">Exibindo <strong>{page}</strong> de <strong>{totalPages}</strong> {totalPages === 1 ? "página" : "páginas"}</span>
    {totalPages > 1 && <nav className="pg-nav" aria-label="Paginação">
      <button type="button" className="pg-btn" aria-label="Página anterior" disabled={page <= 1} onClick={() => onChange(page - 1)}>‹</button>
      {paginasVisiveis(page, totalPages).map((item, index) => item === "…"
        ? <span className="pg-gap" key={`gap-${index}`} aria-hidden="true">…</span>
        : <button type="button" key={item} className="pg-btn" aria-label={`Página ${item}`} aria-current={item === page ? "page" : undefined} onClick={() => onChange(item)}>{item}</button>)}
      <button type="button" className="pg-btn" aria-label="Próxima página" disabled={page >= totalPages} onClick={() => onChange(page + 1)}>›</button>
    </nav>}
  </div>;
}
