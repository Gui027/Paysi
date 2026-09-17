"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Botao, Campo, Dialog, EmptyState, Etiqueta, Select, Skeleton, Toast } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/api";
import {
  archiveCoupon,
  Coupon,
  couponStatus,
  couponStatusLabel,
  couponStatusTone,
  CouponDisplayStatus,
  formatBps,
  formatFixedDiscount,
  listCoupons,
} from "../../../lib/cupons";

function formatDate(value: string | null) {
  return value ? new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(value)) : "—";
}

function discountLabel(coupon: Coupon) {
  return coupon.discountType === "PERCENT" ? formatBps(coupon.discountBps ?? 0) : formatFixedDiscount(coupon.discountCents ?? 0);
}

function consumptionLabel(coupon: Coupon) {
  return coupon.maxRedemptions === null ? `${coupon.redeemedCount} resgatados` : `${coupon.redeemedCount}/${coupon.maxRedemptions} resgatados`;
}

export function CuponsPage() {
  const [coupons, setCoupons] = useState<Coupon[]>([]);
  const [archivedIds, setArchivedIds] = useState<Set<string>>(new Set());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState<"" | CouponDisplayStatus>("");
  const [archiveTarget, setArchiveTarget] = useState<Coupon | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setCoupons(await listCoupons());
    } catch {
      setError("Não foi possível carregar os cupons. Tente novamente.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const visible = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase("pt-BR");
    const now = new Date();
    return coupons
      .map(coupon => ({ coupon, status: couponStatus(coupon, archivedIds.has(coupon.id), now) }))
      .filter(item => (!status || item.status === status) && (!normalized || item.coupon.code.toLocaleLowerCase("pt-BR").includes(normalized)));
  }, [archivedIds, coupons, query, status]);

  async function confirmArchive() {
    if (!archiveTarget || submitting) return;
    setSubmitting(true);
    try {
      await archiveCoupon(archiveTarget.id);
      // A API não expõe mais cupons arquivados (GET filtra archived_at). Mantemos o item na
      // tela, marcado como arquivado, até a próxima recarga — histórico completo depende de
      // um ajuste de contrato no backend.
      setArchivedIds(current => new Set(current).add(archiveTarget.id));
      setSuccess("Cupom arquivado. Ele continua visível nesta tela como histórico até você recarregar a página.");
      setArchiveTarget(null);
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError ? requestError.message : "Não foi possível arquivar o cupom.");
      setArchiveTarget(null);
    } finally {
      setSubmitting(false);
    }
  }

  return <>
    <header className="content-header">
      <div><span className="paysi-rotulo">Marketing</span><h1>Cupons</h1><p>Cadastre cupons e acompanhe o consumo sem esconder limites atingidos.</p></div>
      <Link className="ui-button ui-button-primary" href="/cupons/novo">Novo cupom</Link>
    </header>

    <section className="ui-card affiliate-filters" aria-labelledby="coupon-filter-title">
      <h2 id="coupon-filter-title">Filtros</h2>
      <Campo label="Buscar por código" type="search" value={query} onChange={event => setQuery(event.target.value)} placeholder="Ex.: PROMO10" />
      <Select label="Status" value={status} onChange={event => setStatus(event.target.value as "" | CouponDisplayStatus)}>
        <option value="">Todos</option>
        {Object.entries(couponStatusLabel).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </Select>
      {(query || status) && <Botao variant="secondary" onClick={() => { setQuery(""); setStatus(""); }}>Limpar filtros</Botao>}
    </section>

    {error && <Toast tone="danger">{error} <button className="toast-action" onClick={() => { setError(null); void load(); }}>Tentar novamente</button></Toast>}
    {success && <Toast>{success} <button className="toast-action" onClick={() => setSuccess(null)}>Fechar</button></Toast>}

    {loading ? <Skeleton label="Carregando lista de cupons" /> : coupons.length === 0 ?
      <EmptyState title="Nenhum cupom cadastrado" description="Crie um cupom para oferecer descontos em ofertas específicas." action={<Link className="ui-button ui-button-primary" href="/cupons/novo">Novo cupom</Link>} /> :
      visible.length === 0 ? <EmptyState title="Nenhum resultado" description="Ajuste os filtros para localizar outro cupom." action={<Botao variant="secondary" onClick={() => { setQuery(""); setStatus(""); }}>Limpar filtros</Botao>} /> :
      <section className="affiliate-list" aria-label="Lista de cupons">
        {visible.map(({ coupon, status: itemStatus }) => <article className="ui-card affiliate-row" key={coupon.id}>
          <div className="affiliate-main">
            <div className="ui-labels"><Etiqueta tone={couponStatusTone[itemStatus]}>{couponStatusLabel[itemStatus]}</Etiqueta><span>{coupon.discountType === "PERCENT" ? "Percentual" : "Valor fixo"}</span></div>
            <h2>{coupon.code}</h2>
            <p>Desconto: <strong>{discountLabel(coupon)}</strong></p>
          </div>
          <dl className="affiliate-summary">
            <div><dt>Consumo</dt><dd className="paysi-valor">{consumptionLabel(coupon)}</dd></div>
            <div><dt>Limite por comprador</dt><dd>{coupon.maxPerBuyer}</dd></div>
            <div><dt>Início</dt><dd>{formatDate(coupon.startsAt)}</dd></div>
            <div><dt>Vencimento</dt><dd>{formatDate(coupon.expiresAt)}</dd></div>
            <div><dt>Ofertas</dt><dd>{coupon.offerIds.length}</dd></div>
          </dl>
          <div className="affiliate-actions">
            {itemStatus === "ARCHIVED"
              ? <p className="ui-hint">Arquivado: os dados acima são o histórico deste cupom; a edição não está mais disponível.</p>
              : <><Link className="ui-button ui-button-secondary" href={`/cupons/${coupon.id}`}>Editar</Link><Botao variant="danger" onClick={() => setArchiveTarget(coupon)}>Arquivar</Botao></>}
          </div>
        </article>)}
      </section>}

    <Dialog open={Boolean(archiveTarget)} title="Arquivar cupom" onClose={() => !submitting && setArchiveTarget(null)}>
      <p>O cupom <strong>{archiveTarget?.code}</strong> deixará de poder ser aplicado em novos pedidos. Resgates já feitos não são afetados.</p>
      <div className="ui-actions"><Botao variant="danger" disabled={submitting} onClick={() => void confirmArchive()}>{submitting ? "Arquivando…" : "Confirmar arquivamento"}</Botao></div>
    </Dialog>
  </>;
}
