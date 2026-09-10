"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Botao, Checkbox, Dialog, EmptyState, Select, Skeleton, Toast } from "../../../components/ui";
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

const segmentLabel: Record<MarketplaceSegment, string> = { SAAS: "SaaS", DIGITAL: "Produto digital" };
const chargeLabel: Record<MarketplaceChargeType, string> = { ONE_TIME: "Pagamento único", SUBSCRIPTION: "Assinatura" };

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

export function VitrinePage() {
  const [items, setItems] = useState<MarketplaceItem[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<RequestFeedback | null>(null);
  const [query, setQuery] = useState("");
  const [segment, setSegment] = useState<"" | MarketplaceSegment>("");
  const [chargeType, setChargeType] = useState<"" | MarketplaceChargeType>("");
  const [selected, setSelected] = useState<MarketplaceItem | null>(null);
  const [accepted, setAccepted] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [existing, setExisting] = useState<Map<string, AffiliationStatus>>(() => new Map());

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
      setError("Não foi possível carregar a vitrine. Tente novamente.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const visible = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase("pt-BR");
    return items.filter(item => (!segment || item.segment === segment)
      && (!chargeType || item.chargeType === chargeType)
      && (!normalized || item.product.toLocaleLowerCase("pt-BR").includes(normalized)
        || item.seller.toLocaleLowerCase("pt-BR").includes(normalized)));
  }, [items, query, segment, chargeType]);

  function openDetails(item: MarketplaceItem) {
    setSelected(item);
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
      setFeedback({ tone: "success", message: "Solicitação enviada. O vendedor fará a análise dos termos." });
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

  const hasFilters = Boolean(query || segment || chargeType);
  const clearFilters = () => { setQuery(""); setSegment(""); setChargeType(""); };

  return <>
    <header className="content-header marketplace-heading"><div><span className="paysi-rotulo">Modo afiliado</span><h1>Vitrine</h1><p>Descubra produtos publicados e confira todas as condições antes de solicitar a afiliação.</p></div></header>

    <section className="ui-card marketplace-filters" aria-labelledby="marketplace-filter-title">
      <h2 id="marketplace-filter-title">Filtros</h2>
      <label className="ui-field"><span>Buscar por produto ou vendedor</span><input type="search" value={query} onChange={event => setQuery(event.target.value)} placeholder="Ex.: Curso" /></label>
      <Select label="Segmento" value={segment} onChange={event => setSegment(event.target.value as "" | MarketplaceSegment)}><option value="">Todos</option><option value="SAAS">SaaS</option><option value="DIGITAL">Produto digital</option></Select>
      <Select label="Cobrança" value={chargeType} onChange={event => setChargeType(event.target.value as "" | MarketplaceChargeType)}><option value="">Todas</option><option value="ONE_TIME">Pagamento único</option><option value="SUBSCRIPTION">Assinatura</option></Select>
      {hasFilters && <Botao variant="secondary" onClick={clearFilters}>Limpar filtros</Botao>}
    </section>

    {error && <Toast tone="danger">{error} <button className="toast-action" onClick={() => void load()}>Tentar novamente</button></Toast>}
    {feedback && <Toast tone={feedback.tone}>{feedback.message} {feedback.kyc && <Link className="toast-action" href="/inicio">Ver orientação no início</Link>} <button className="toast-action" onClick={() => setFeedback(null)}>Fechar</button></Toast>}

    {loading ? <Skeleton label="Carregando produtos da vitrine" /> : items.length === 0 ?
      <EmptyState title="Nenhum produto disponível" description="Quando vendedores publicarem produtos afiliáveis, eles aparecerão aqui." /> :
      visible.length === 0 ? <EmptyState title="Nenhum resultado" description="Ajuste os filtros para encontrar outros produtos." action={<Botao variant="secondary" onClick={clearFilters}>Limpar filtros</Botao>} /> :
      <section className="marketplace-grid" aria-label="Produtos disponíveis para afiliação">
        {visible.map(item => <article className="ui-card marketplace-card" key={item.productId}>
          <div className="ui-labels"><span className="ui-label">{segmentLabel[item.segment]}</span><span>{chargeLabel[item.chargeType]}</span>{existing.get(item.productId) && <span className="ui-label ui-label-success">{affiliationStatusLabel[existing.get(item.productId)!]}</span>}</div>
          <div><h2>{item.product}</h2><p>{item.description || "O vendedor não adicionou uma descrição."}</p></div>
          <p className="marketplace-seller">Por <strong>{item.seller}</strong></p>
          <dl className="marketplace-summary"><div><dt>A partir de</dt><dd className="paysi-valor">{formatarCentavos(item.startingPriceCents)}</dd></div><div><dt>Comissão estimada</dt><dd>{item.suggestedCommissionBps === null ? "Definida na análise" : formatCommissionBps(item.suggestedCommissionBps)}</dd></div></dl>
          <small className="marketplace-estimate">A estimativa não é promessa de ganho. A comissão final é definida pelo vendedor.</small>
          <Botao disabled={existing.has(item.productId)} onClick={() => openDetails(item)}>{existing.has(item.productId) ? affiliationStatusLabel[existing.get(item.productId)!] : "Ver condições"}</Botao>
        </article>)}
      </section>}
    {nextCursor && !loading && <div className="load-more"><Botao variant="secondary" disabled={loadingMore} onClick={() => void loadMore()}>{loadingMore ? "Carregando…" : "Carregar mais"}</Botao></div>}

    <Dialog open={Boolean(selected)} title="Condições da afiliação" onClose={() => !submitting && setSelected(null)}>
      {selected && <><h3>{selected.product}</h3><p>Vendido por <strong>{selected.seller}</strong></p><dl className="detail-list marketplace-terms"><div><dt>Preço inicial</dt><dd className="paysi-valor">{formatarCentavos(selected.startingPriceCents)}</dd></div><div><dt>Comissão proposta</dt><dd>{selected.suggestedCommissionBps === null ? "Será definida pelo vendedor" : `${formatCommissionBps(selected.suggestedCommissionBps)} (estimativa)`}</dd></div><div><dt>Recorrência</dt><dd>Será definida pelo vendedor na aprovação</dd></div><div><dt>Garantia</dt><dd>{selected.guaranteeDays} dias</dd></div><div><dt>Janela de atribuição</dt><dd>{selected.attributionDays} dias</dd></div><div><dt>Liberação prevista</dt><dd>Após {selected.payoutDelayDays} dias, respeitadas as demais regras</dd></div></dl><p className="draft-note">A comissão exibida é uma estimativa, não uma promessa de ganho. Pedidos duplicados não criam novos vínculos.</p><Checkbox label="Li e entendi as condições apresentadas" checked={accepted} onChange={event => setAccepted(event.target.checked)} /><div className="ui-actions"><Botao disabled={!accepted || submitting} onClick={() => void submitRequest()}>{submitting ? "Enviando…" : "Solicitar afiliação"}</Botao></div></>}
    </Dialog>
  </>;
}
