"use client";

import Link from "next/link";
import { useState } from "react";
import { filterCategories } from "../../lib/ajuda";
import { CategoriaIcone } from "./CategoriaIcone";

/** Busca da Central de Ajuda e a grade de categorias. A busca filtra as categorias enquanto se digita. */
export function AjudaBusca() {
  const [query, setQuery] = useState("");
  const items = filterCategories(query);

  return <>
    <section className="aj-hero"><h1>Como podemos ajudar?</h1>
    <form className="aj-search" role="search" onSubmit={event => event.preventDefault()}>
      <label><span className="sr-only">Buscar em nossa Central de Ajuda</span>
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true" focusable="false"><circle cx="11" cy="11" r="7" /><path d="m20 20-3.500-3.500" /></svg>
        <input type="search" value={query} onChange={event => setQuery(event.target.value)} placeholder="Buscar em nossa Central de Ajuda" />
      </label>
    </form></section>
    <div className="aj-body aj-wrap">
      <h2>Buscar em todas as categorias</h2>
      {items.length === 0 ? <p className="aj-empty" role="status">Nenhuma categoria encontrada para “{query}”.</p> :
        <ul className="aj-grid">{items.map(category => <li key={category.slug}>
          <Link href={`/ajuda/${category.slug}`} className="aj-card">
            <CategoriaIcone slug={category.slug} />
            <span className="aj-card-text"><span className="aj-tag">{category.title}</span><span>{category.description}</span></span>
          </Link>
        </li>)}</ul>}
    </div>
  </>;
}
