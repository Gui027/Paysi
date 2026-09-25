"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";
import { ApiRequestError } from "../../../lib/api";
import {
  archiveProduct,
  listProducts,
  Product,
  ProductChargeType,
  ProductFilters,
  ProductSegment,
  ProductStatus,
  productChargeTypeLabel,
  productMatchesFilters,
  productSegmentLabel,
  productStatusLabel,
} from "../../../lib/produtos";
import { CriarProdutoModal } from "./CriarProdutoModal";
import { Botao, Dialog, EmptyState, Etiqueta, Skeleton, Toast } from "../../../components/ui";

const emptyFilters: ProductFilters = { query: "", status: "", segment: "", chargeType: "" };

function statusTone(status: ProductStatus): "neutral" | "success" | "warning" | "danger" {
  if (status === "ACTIVE") return "success";
  if (status === "PAUSED") return "warning";
  if (status === "SUSPENDED") return "danger";
  return "neutral";
}

export function ProdutosPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [products, setProducts] = useState<Product[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [archiveTarget, setArchiveTarget] = useState<Product | null>(null);
  const [archiving, setArchiving] = useState(false);
  const [creating, setCreating] = useState(false);

  const filters = useMemo<ProductFilters>(() => ({
    query: searchParams.get("q") ?? "",
    status: (searchParams.get("status") as ProductStatus | null) ?? "",
    segment: (searchParams.get("segment") as ProductSegment | null) ?? "",
    chargeType: (searchParams.get("chargeType") as ProductChargeType | null) ?? "",
  }), [searchParams]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const page = await listProducts();
      setProducts(page.items);
      setNextCursor(page.nextCursor);
    } catch {
      setError("Não foi possível carregar os produtos. Tente novamente.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  function updateFilter(name: keyof ProductFilters, value: string) {
    const query = new URLSearchParams(searchParams.toString());
    if (value) query.set(name === "query" ? "q" : name, value);
    else query.delete(name === "query" ? "q" : name);
    router.replace(query.size ? `/produtos?${query}` : "/produtos", { scroll: false });
  }

  async function loadMore() {
    if (!nextCursor || loadingMore) return;
    setLoadingMore(true);
    try {
      const page = await listProducts(nextCursor);
      setProducts(current => [...current, ...page.items]);
      setNextCursor(page.nextCursor);
    } catch {
      setError("Não foi possível carregar mais produtos.");
    } finally {
      setLoadingMore(false);
    }
  }

  async function confirmArchive() {
    if (!archiveTarget || archiving) return;
    setArchiving(true);
    try {
      await archiveProduct(archiveTarget.id);
      setProducts(current => current.filter(product => product.id !== archiveTarget.id));
      setArchiveTarget(null);
    } catch (requestError) {
      const message = requestError instanceof ApiRequestError && requestError.status === 404
        ? "O produto não está mais disponível."
        : "Não foi possível arquivar o produto.";
      setError(message);
      setArchiveTarget(null);
    } finally {
      setArchiving(false);
    }
  }

  const visibleProducts = products.filter(product => productMatchesFilters(product, filters));
  const hasFilters = Object.values(filters).some(Boolean);

  return <>
    <header className="prod-head">
      <h1>Produtos</h1>
      <button type="button" className="ui-button ui-button-primary" onClick={() => setCreating(true)}>Criar produto</button>
    </header>
    <CriarProdutoModal open={creating} onClose={() => setCreating(false)} />

    <section className="prod-panel" aria-label="Lista de produtos">
      <div className="prod-toolbar">
        <label className="prod-search"><span className="sr-only">Buscar por nome</span>
          <input type="search" value={filters.query} onChange={event => updateFilter("query", event.target.value)} placeholder="Buscar…" /></label>
        <Link className="prod-link" href="/cupons">Cupons de desconto</Link>
        <label className="prod-status"><span className="sr-only">Status</span>
          <select value={filters.status} onChange={event => updateFilter("status", event.target.value)}>
            <option value="">Todos</option><option value="DRAFT">Rascunho</option><option value="ACTIVE">Publicado</option><option value="PAUSED">Pausado</option><option value="SUSPENDED">Suspenso</option>
          </select></label>
      </div>

      {error && <Toast tone="danger">{error} <button className="toast-action" onClick={() => void load()}>Tentar novamente</button></Toast>}
      {loading ? <Skeleton label="Carregando lista de produtos" /> : products.length === 0 ?
        <EmptyState title="Nenhum produto cadastrado" description="Crie um produto em rascunho para começar." action={<button type="button" className="ui-button ui-button-primary" onClick={() => setCreating(true)}>Criar produto</button>} /> :
        visibleProducts.length === 0 ? <EmptyState title="Nenhum resultado" description="Ajuste ou limpe os filtros para localizar outro produto." action={hasFilters ? <Botao variant="secondary" onClick={() => router.replace("/produtos", { scroll: false })}>Limpar filtros</Botao> : undefined} /> :
        <table className="prod-table">
          <thead><tr><th scope="col">Nome</th><th scope="col">Cobrança</th><th scope="col">Status</th><th scope="col"><span className="sr-only">Ações</span></th></tr></thead>
          <tbody>{visibleProducts.map(product => <tr key={product.id}>
            <td><Link className="prod-name" href={`/produtos/${product.id}`}>{product.name}</Link></td>
            <td className="prod-muted">{productChargeTypeLabel[product.chargeType]} · {productSegmentLabel[product.segment]}</td>
            <td><Etiqueta tone={statusTone(product.status)}>{productStatusLabel[product.status]}</Etiqueta></td>
            <td className="prod-actions">
              <details className="prod-menu">
                <summary aria-label={`Ações de ${product.name}`}>⋮</summary>
                <div className="prod-menu-list">
                  <Link href={`/produtos/${product.id}`}>Ver detalhes</Link>
                  <Link href={`/produtos/${product.id}/editar`}>Editar</Link>
                  <button type="button" onClick={() => setArchiveTarget(product)}>Arquivar</button>
                </div>
              </details>
            </td>
          </tr>)}</tbody>
        </table>}
    </section>
    {nextCursor && !loading && <div className="load-more"><Botao variant="secondary" disabled={loadingMore} onClick={() => void loadMore()}>{loadingMore ? "Carregando…" : "Carregar mais"}</Botao></div>}

    <Dialog open={Boolean(archiveTarget)} title="Arquivar produto" onClose={() => !archiving && setArchiveTarget(null)}>
      <p>O produto <strong>{archiveTarget?.name}</strong> deixará de aparecer na lista. Esta ação não publica nem altera ofertas.</p>
      <div className="ui-actions"><Botao variant="danger" disabled={archiving} onClick={() => void confirmArchive()}>{archiving ? "Arquivando…" : "Confirmar arquivamento"}</Botao></div>
    </Dialog>
  </>;
}
