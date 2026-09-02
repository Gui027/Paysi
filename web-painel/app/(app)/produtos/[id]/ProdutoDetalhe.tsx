"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ApiRequestError } from "../../../../lib/api";
import { getProduct, Product, productChargeTypeLabel, productSegmentLabel, productStatusLabel } from "../../../../lib/produtos";
import { EmptyState, Etiqueta, Skeleton, Toast } from "../../../../components/ui";

export function ProdutoDetalhe({ productId }: { productId: string }) {
  const [product, setProduct] = useState<Product | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [error, setError] = useState(false);

  useEffect(() => {
    let active = true;
    setLoading(true);
    getProduct(productId).then(value => { if (active) setProduct(value); }).catch(requestError => {
      if (!active) return;
      if (requestError instanceof ApiRequestError && requestError.status === 404) setNotFound(true);
      else setError(true);
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [productId]);

  if (loading) return <Skeleton label="Carregando detalhe do produto" />;
  if (notFound) return <EmptyState title="Produto não encontrado" description="O produto não existe ou não está disponível para esta conta." action={<Link className="ui-button ui-button-secondary" href="/produtos">Voltar aos produtos</Link>} />;
  if (error || !product) return <Toast tone="danger">Não foi possível carregar o produto. <Link href="/produtos">Voltar aos produtos</Link></Toast>;

  return <>
    <nav className="breadcrumb" aria-label="Navegação estrutural"><Link href="/produtos">Produtos</Link><span aria-hidden="true">/</span><span aria-current="page">Detalhe</span></nav>
    <header className="content-header products-heading"><div><div className="ui-labels"><Etiqueta tone={product.status === "ACTIVE" ? "success" : "neutral"}>{productStatusLabel[product.status]}</Etiqueta><span>{productSegmentLabel[product.segment]}</span></div><h1>{product.name}</h1><p>{product.description || "Sem descrição."}</p></div><Link className="ui-button ui-button-secondary" href={`/produtos/${product.id}/editar`}>Editar produto</Link></header>
    <div className="product-detail-grid">
      <section className="ui-card" aria-labelledby="operational-title"><h2 id="operational-title">Dados operacionais</h2><dl className="detail-list"><div><dt>Tipo de cobrança</dt><dd>{productChargeTypeLabel[product.chargeType]}</dd></div><div><dt>Afiliação</dt><dd>{product.affiliationEnabled ? "Permitida" : "Desativada"}</dd></div><div><dt>Criado em</dt><dd>{new Intl.DateTimeFormat("pt-BR", { dateStyle: "long", timeStyle: "short" }).format(new Date(product.createdAt))}</dd></div><div><dt>Identificador</dt><dd><code>{product.id}</code></dd></div></dl></section>
      <section className="ui-card" aria-labelledby="offers-title"><h2 id="offers-title">Ofertas</h2><EmptyState title="Nenhuma oferta disponível" description={product.status === "DRAFT" ? "Produtos em rascunho ainda não possuem checkout publicado." : "O contrato atual não disponibiliza ofertas no detalhe deste produto."} /></section>
    </div>
  </>;
}
