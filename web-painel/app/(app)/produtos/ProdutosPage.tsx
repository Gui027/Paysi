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
import { Botao, Dialog, EmptyState, Etiqueta, Select, Skeleton, Toast } from "../../../components/ui";

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
    <header className="content-header products-heading">
      <div><h1>Produtos</h1><p>Localize seus produtos e acompanhe o estado de publicação.</p></div>
      <Link className="ui-button ui-button-primary product-create" href="/produtos/novo">Novo produto</Link>
    </header>

    <section className="ui-card product-filters" aria-labelledby="product-filter-title">
      <h2 id="product-filter-title">Filtros</h2>
      <label className="ui-field product-search"><span>Buscar por nome</span><input type="search" value={filters.query} onChange={event => updateFilter("query", event.target.value)} placeholder="Ex.: Curso de vendas" /></label>
      <Select label="Status" value={filters.status} onChange={event => updateFilter("status", event.target.value)}>
        <option value="">Todos</option><option value="DRAFT">Rascunho</option><option value="ACTIVE">Publicado</option><option value="PAUSED">Pausado</option><option value="SUSPENDED">Suspenso</option>
      </Select>
      <Select label="Segmento" value={filters.segment} onChange={event => updateFilter("segment", event.target.value)}>
        <option value="">Todos</option><option value="SAAS">SaaS</option><option value="DIGITAL">Produto digital</option>
      </Select>
      <Select label="Cobrança" value={filters.chargeType} onChange={event => updateFilter("chargeType", event.target.value)}>
        <option value="">Todas</option><option value="ONE_TIME">Pagamento único</option><option value="SUBSCRIPTION">Assinatura</option>
      </Select>
      {hasFilters && <Botao variant="secondary" onClick={() => router.replace("/produtos", { scroll: false })}>Limpar filtros</Botao>}
    </section>

    {error && <Toast tone="danger">{error} <button className="toast-action" onClick={() => void load()}>Tentar novamente</button></Toast>}
    {loading ? <Skeleton label="Carregando lista de produtos" /> : products.length === 0 ?
      <EmptyState title="Nenhum produto cadastrado" description="Crie um produto em rascunho para começar." action={<Link className="ui-button ui-button-primary" href="/produtos/novo">Criar produto</Link>} /> :
      visibleProducts.length === 0 ? <EmptyState title="Nenhum resultado" description="Ajuste ou limpe os filtros para localizar outro produto." action={<Botao variant="secondary" onClick={() => router.replace("/produtos", { scroll: false })}>Limpar filtros</Botao>} /> :
      <section className="product-list" aria-label="Lista de produtos">
        {visibleProducts.map(product => <article className="ui-card product-row" key={product.id}>
          <div className="product-main"><div className="ui-labels"><Etiqueta tone={statusTone(product.status)}>{productStatusLabel[product.status]}</Etiqueta><span>{productSegmentLabel[product.segment]}</span></div><h2><Link href={`/produtos/${product.id}`}>{product.name}</Link></h2><p>{product.description || "Sem descrição."}</p></div>
          <dl className="product-meta"><div><dt>Cobrança</dt><dd>{productChargeTypeLabel[product.chargeType]}</dd></div><div><dt>Criado em</dt><dd>{new Intl.DateTimeFormat("pt-BR").format(new Date(product.createdAt))}</dd></div></dl>
          <div className="product-actions"><Link className="ui-button ui-button-secondary" href={`/produtos/${product.id}`}>Ver detalhes</Link><Link className="ui-button ui-button-secondary" href={`/produtos/${product.id}/editar`}>Editar</Link><Botao variant="danger" onClick={() => setArchiveTarget(product)}>Arquivar</Botao></div>
        </article>)}
      </section>}
    {nextCursor && !loading && <div className="load-more"><Botao variant="secondary" disabled={loadingMore} onClick={() => void loadMore()}>{loadingMore ? "Carregando…" : "Carregar mais"}</Botao></div>}

    <Dialog open={Boolean(archiveTarget)} title="Arquivar produto" onClose={() => !archiving && setArchiveTarget(null)}>
      <p>O produto <strong>{archiveTarget?.name}</strong> deixará de aparecer na lista. Esta ação não publica nem altera ofertas.</p>
      <div className="ui-actions"><Botao variant="danger" disabled={archiving} onClick={() => void confirmArchive()}>{archiving ? "Arquivando…" : "Confirmar arquivamento"}</Botao></div>
    </Dialog>
  </>;
}
