"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ApiRequestError } from "../../../../lib/api";
import { getProduct, Product, productChargeTypeLabel, productSegmentLabel, productStatusLabel } from "../../../../lib/produtos";
import { formatOfferMoney, listOffers, Offer } from "../../../../lib/ofertas";
import { EmptyState, Etiqueta, Skeleton, Toast } from "../../../../components/ui";

export function ProdutoDetalhe({ productId }: { productId: string }) {
  const [product, setProduct] = useState<Product | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [error, setError] = useState(false);
  const [offers, setOffers] = useState<Offer[]>([]);

  useEffect(() => {
    let active = true;
    setLoading(true);
    Promise.all([getProduct(productId), listOffers(productId)]).then(([value, loadedOffers]) => { if (active) { setProduct(value); setOffers(loadedOffers); } }).catch(requestError => {
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
      <section className="ui-card" aria-labelledby="offers-title"><div className="section-heading"><div><h2 id="offers-title">Ofertas</h2><p>Configure preço, meios de pagamento e publicação.</p></div><Link className="ui-button" href={`/produtos/${product.id}/ofertas`}>Criar oferta</Link></div>{offers.length === 0 ? <EmptyState title="Nenhuma oferta disponível" description={product.status === "DRAFT" ? "Crie uma oferta para preparar o checkout do produto." : "Este produto ainda não possui ofertas."} headingLevel="h3" action={<Link className="ui-button ui-button-secondary" href={`/produtos/${product.id}/ofertas`}>Configurar oferta</Link>} /> : <div className="offer-list">{offers.map(offer => <article className="offer-list-item" key={offer.id}><div><strong>{formatOfferMoney(offer.priceCents)}</strong><span>{offer.cycle ? `Ciclo ${offer.cycle.toLocaleLowerCase()}` : "Pagamento único"}</span><span className={`ui-label ui-label-${offer.status === "PUBLISHED" ? "success" : "neutral"}`}>{offer.status === "PUBLISHED" ? "Publicada" : "Rascunho"}</span></div><Link className="ui-button ui-button-secondary" href={`/produtos/${product.id}/ofertas/${offer.id}`}>Editar</Link></article>)}</div>}</section>
    </div>
  </>;
}
