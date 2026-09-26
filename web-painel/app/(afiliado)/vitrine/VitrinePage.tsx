"use client";

import Link from "next/link";
import { useCallback, useEffect, useId, useMemo, useRef, useState } from "react";
import { EmptyState, Skeleton, Toast } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/api";
import { formatarCentavos } from "../../../lib/moeda";
import {
  MarketplaceChargeType,
  MarketplaceItem,
  MarketplaceSegment,
  AffiliationStatus,
  affiliationStatusLabel,
  formatCommissionBps,
  listAffiliateAffiliations,
  listMarketplace,
  requestAffiliation,
} from "../../../lib/afiliados";

const segmentLabel: Record<MarketplaceSegment, string> = { SAAS: "Software (SaaS)", DIGITAL: "Produto digital" };
const chargeLabel: Record<MarketplaceChargeType, string> = { ONE_TIME: "Venda única", SUBSCRIPTION: "Assinatura" };
const COVER_COLORS = ["#1D6BD8", "#0F2A4E", "#4B3BB0", "#0B6B3A", "#B4531A", "#8A2F6B"];

type RequestFeedback = { tone: "success" | "danger"; message: string; kyc?: boolean };

function requestError(error: unknown): RequestFeedback {
  if (error instanceof ApiRequestError && error.problem.code === "AFFILIATE_KYC_REQUIRED") {
    return { tone: "danger", message: "Conclua a verificação da conta antes de solicitar uma afiliação.", kyc: true };
  }
  if (error instanceof ApiRequestError && error.problem.code === "AFFILIATION_ALREADY_ACTIVE") {
    return { tone: "danger", message: "Você já possui uma solicitação ou afiliação ativa para este produto." };
  }
  if (error instanceof ApiRequestError && error.problem.code === "SELF_AFFILIATION_FORBIDDEN") {
    return { tone: "danger", message: "Não é possível solicitar afiliação ao seu próprio produto." };
  }
  return { tone: "danger", message: "Não foi possível enviar a solicitação. Tente novamente." };
}

// Capa provisória: ainda não há imagem do produto, então usamos as iniciais sobre uma cor estável.
function Capa({ nome, pequena = false }: { nome: string; pequena?: boolean }) {
  const iniciais = nome.split(/\s+/).filter(Boolean).slice(0, 2).map(parte => parte[0]!.toUpperCase()).join("");
  let hash = 0;
  for (const letra of nome) hash = (hash * 31 + letra.charCodeAt(0)) % COVER_COLORS.length;
  return <div className={`mk-cover ${pequena ? "mk-cover-small" : ""}`} style={{ background: COVER_COLORS[hash] }} aria-hidden="true"><span>{iniciais || "P"}</span></div>;
}

function receba(item: MarketplaceItem) {
  return item.maxCommissionCents === null || item.maxCommissionCents === undefined ? "Definida pelo vendedor" : formatarCentavos(item.maxCommissionCents);
}

export function VitrinePage() {
  const [items, setItems] = useState<MarketplaceItem[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<RequestFeedback | null>(null);
  const [query, setQuery] = useState("");
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [segment, setSegment] = useState<"" | MarketplaceSegment>("");
  const [chargeType, setChargeType] = useState<"" | MarketplaceChargeType>("");
  const [selected, setSelected] = useState<MarketplaceItem | null>(null);
  const [tab, setTab] = useState<"produto" | "detalhes">("produto");
  const [accepted, setAccepted] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [existing, setExisting] = useState<Map<string, AffiliationStatus>>(() => new Map());
  const drawerRef = useRef<HTMLDialogElement>(null);
  const titleId = useId();

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [page, affiliations] = await Promise.all([listMarketplace(), listAffiliateAffiliations()]);
      setItems(page.items);
      setNextCursor(page.nextCursor);
      setExisting(new Map(affiliations.items
        .filter(item => item.status === "PENDING" || item.status === "APPROVED")
        .map(item => [item.productId, item.status])));
    } catch {
      setError("Não foi possível carregar o marketplace. Tente novamente.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  useEffect(() => {
    const dialog = drawerRef.current;
    if (!dialog) return;
    if (selected && !dialog.open) dialog.showModal();
    if (!selected && dialog.open) dialog.close();
  }, [selected]);

  const visible = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase("pt-BR");
    return items.filter(item => (!segment || item.segment === segment)
      && (!chargeType || item.chargeType === chargeType)
      && (!normalized || item.product.toLocaleLowerCase("pt-BR").includes(normalized)
        || item.seller.toLocaleLowerCase("pt-BR").includes(normalized)));
  }, [items, query, segment, chargeType]);

  function openDetails(item: MarketplaceItem) {
    setSelected(item);
    setTab("produto");
    setAccepted(false);
    setFeedback(null);
  }

  async function submitRequest() {
    if (!selected || !accepted || submitting || existing.has(selected.productId)) return;
    setSubmitting(true);
    setFeedback(null);
    try {
      const affiliation = await requestAffiliation(selected.productId);
      setExisting(current => new Map(current).set(selected.productId, affiliation.status));
      setFeedback({
        tone: "success",
        message: affiliation.status === "APPROVED"
          ? "Afiliação aprovada! Seu link de divulgação já está em Meus links."
          : "Solicitação enviada. O vendedor fará a análise e você será avisado na aprovação.",
      });
      setSelected(null);
    } catch (requestFailure) {
      const nextFeedback = requestError(requestFailure);
      if (requestFailure instanceof ApiRequestError && requestFailure.problem.code === "AFFILIATION_ALREADY_ACTIVE") {
        setExisting(current => new Map(current).set(selected.productId, "PENDING"));
      }
      setFeedback(nextFeedback);
      setSelected(null);
    } finally {
      setSubmitting(false);
    }
  }

  async function loadMore() {
    if (!nextCursor || loadingMore) return;
    setLoadingMore(true);
    try {
      const page = await listMarketplace(nextCursor);
      setItems(current => [...current, ...page.items]);
      setNextCursor(page.nextCursor);
    } catch {
      setError("Não foi possível carregar mais produtos.");
    } finally {
      setLoadingMore(false);
    }
  }

  const hasFilters = Boolean(segment || chargeType);
  const clearFilters = () => { setQuery(""); setSegment(""); setChargeType(""); };
  const situacao = selected ? existing.get(selected.productId) : undefined;

  return <div className="mk">
    <header className="prod-head"><h1>Marketplace</h1></header>

    <section className="mk-search" aria-label="Buscar produtos">
      <div className="mk-search-bar">
        <label className="mk-search-field"><span className="sr-only">Buscar por produto ou vendedor</span>
          <input type="search" value={query} onChange={event => setQuery(event.target.value)} placeholder="Buscar produto ou vendedor" /></label>
        <button type="button" className="mk-filter-button" aria-expanded={filtersOpen} aria-controls="mk-filtros" onClick={() => setFiltersOpen(open => !open)}>Filtros{hasFilters ? " •" : ""}</button>
      </div>
      {filtersOpen && <div id="mk-filtros" className="mk-filters">
        <label className="pe-field"><span>Tipo de produto</span><select value={segment} onChange={event => setSegment(event.target.value as "" | MarketplaceSegment)}><option value="">Todos</option><option value="SAAS">Software (SaaS)</option><option value="DIGITAL">Produto digital</option></select></label>
        <label className="pe-field"><span>Cobrança</span><select value={chargeType} onChange={event => setChargeType(event.target.value as "" | MarketplaceChargeType)}><option value="">Todas</option><option value="ONE_TIME">Venda única</option><option value="SUBSCRIPTION">Assinatura</option></select></label>
        {(hasFilters || query) && <button type="button" className="ui-button ui-button-secondary" onClick={clearFilters}>Limpar filtros</button>}
      </div>}
    </section>

    {error && <Toast tone="danger">{error} <button className="toast-action" onClick={() => void load()}>Tentar novamente</button></Toast>}
    {feedback && <Toast tone={feedback.tone}>{feedback.message} {feedback.kyc && <Link className="toast-action" href="/verificacao">Verificar minha conta</Link>} <button className="toast-action" onClick={() => setFeedback(null)}>Fechar</button></Toast>}

    {loading ? <Skeleton label="Carregando produtos do marketplace" /> : items.length === 0 ?
      <EmptyState title="Nenhum produto disponível" description="Quando vendedores publicarem produtos com programa de afiliados, eles aparecerão aqui." /> :
      visible.length === 0 ? <EmptyState title="Nenhum resultado" description="Ajuste a busca ou os filtros para encontrar outros produtos." action={<button type="button" className="ui-button ui-button-secondary" onClick={clearFilters}>Limpar filtros</button>} /> :
      <section className="mk-grid" aria-label="Produtos disponíveis para afiliação">
        {visible.map(item => {
          const status = existing.get(item.productId);
          return <button type="button" className="mk-card" key={item.productId} onClick={() => openDetails(item)} aria-label={`${item.product}, por ${item.seller}. Abrir detalhes`}>
            <Capa nome={item.product} />
            <span className="mk-card-body">
              <strong className="mk-card-title">{item.product}</strong>
              <span className="mk-card-seller">Por {item.seller}</span>
              {status && <span className="pe-pill pe-pill-on">{affiliationStatusLabel[status]}</span>}
              <span className="mk-card-earn"><span>Receba até</span><strong>{receba(item)}</strong></span>
              <span className="mk-card-price">Preço a partir de: {formatarCentavos(item.startingPriceCents)}</span>
            </span>
          </button>;
        })}
      </section>}
    {nextCursor && !loading && <div className="load-more"><button type="button" className="ui-button ui-button-secondary" disabled={loadingMore} onClick={() => void loadMore()}>{loadingMore ? "Carregando…" : "Carregar mais"}</button></div>}

    <dialog ref={drawerRef} className="mk-drawer" aria-labelledby={titleId} onCancel={event => { event.preventDefault(); if (!submitting) setSelected(null); }}>
      {selected && <>
        <header className="mk-drawer-head">
          <Capa nome={selected.product} pequena />
          <div className="mk-drawer-title"><h2 id={titleId}>{selected.product}</h2><p>Por {selected.seller}</p></div>
          <button type="button" className="cp-close" aria-label="Fechar" onClick={() => !submitting && setSelected(null)}>✕</button>
        </header>
        <div className="mk-drawer-cta">
          {situacao ? <span className="pe-pill pe-pill-on">{situacao === "APPROVED" ? "Você já é um afiliado" : "Solicitação em análise"}</span> : <>
            <label className="mk-terms"><input type="checkbox" checked={accepted} onChange={event => setAccepted(event.target.checked)} /> Li e entendi as condições</label>
            <button type="button" className="ui-button ui-button-primary" disabled={!accepted || submitting} onClick={() => void submitRequest()}>{submitting ? "Enviando…" : "Solicitar afiliação"}</button>
          </>}
        </div>
        <div className="pe-tabs mk-tabs" role="tablist" aria-label="Detalhes do produto">
          <button type="button" role="tab" id="mk-tab-produto" aria-selected={tab === "produto"} aria-controls="mk-painel" onClick={() => setTab("produto")}>Produto</button>
          <button type="button" role="tab" id="mk-tab-detalhes" aria-selected={tab === "detalhes"} aria-controls="mk-painel" onClick={() => setTab("detalhes")}>Detalhes</button>
        </div>
        <div id="mk-painel" role="tabpanel" aria-labelledby={`mk-tab-${tab}`} className="mk-drawer-body">
          {tab === "produto" ? <dl className="mk-facts">
            <div><dt>Tipo</dt><dd>{chargeLabel[selected.chargeType]}</dd></div>
            <div><dt>Categoria</dt><dd>{segmentLabel[selected.segment]}</dd></div>
            <div><dt>Receba até</dt><dd className="mk-earn">{receba(selected)}</dd></div>
            <div><dt>Comissão</dt><dd>{selected.suggestedCommissionBps === null ? "Definida na aprovação" : formatCommissionBps(selected.suggestedCommissionBps)}</dd></div>
            <div><dt>Preço a partir de</dt><dd>{formatarCentavos(selected.startingPriceCents)}</dd></div>
          </dl> : <dl className="mk-facts">
            <div><dt>Sobre o produto</dt><dd>{selected.description || "O vendedor não adicionou uma descrição."}</dd></div>
            <div><dt>Garantia</dt><dd>{selected.guaranteeDays} dias</dd></div>
            <div><dt>Janela de atribuição</dt><dd>{selected.attributionDays} dias após o clique</dd></div>
            <div><dt>Liberação prevista</dt><dd>{selected.payoutDelayDays} dias, respeitadas as demais regras</dd></div>
          </dl>}
          <p className="pe-hint">O valor é uma estimativa, não uma promessa de ganho. Pedidos duplicados não criam novos vínculos.</p>
        </div>
      </>}
    </dialog>
  </div>;
}
