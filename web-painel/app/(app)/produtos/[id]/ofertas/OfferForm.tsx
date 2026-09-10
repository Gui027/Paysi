"use client";

import Link from "next/link";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { ApiRequestError, fieldErrors } from "../../../../../lib/api";
import { getProduct, Product } from "../../../../../lib/produtos";
import {
  BillingCycle, createOffer, formatOfferMoney, getOffer, Offer, OfferImmutableField, OfferInput,
  OfferInputErrors, OfferPaymentMethod, OfferPayoutDelay, parseMoneyToCents, publishOffer,
  simulateOffer, updateOffer, validateOfferInput,
} from "../../../../../lib/ofertas";
import { Botao, Campo, Checkbox, EmptyState, Select, Skeleton, Toast } from "../../../../../components/ui";

const blankInput: OfferInput = {
  priceCents: 0,
  cycle: null,
  trialDays: 0,
  trialRequiresCard: true,
  guaranteeDays: 7,
  maxInstallments: 1,
  boletoDueDays: 3,
  boletoAdvanceDays: 5,
  paymentMethods: ["PIX", "CARD"],
  payoutDelay: "D32",
};

function inputFromOffer(offer: Offer): OfferInput {
  return {
    priceCents: offer.priceCents,
    cycle: offer.cycle,
    trialDays: offer.trialDays,
    trialRequiresCard: offer.trialRequiresCard,
    guaranteeDays: offer.guaranteeDays,
    maxInstallments: offer.maxInstallments,
    boletoDueDays: offer.boletoDueDays,
    boletoAdvanceDays: offer.boletoAdvanceDays,
    paymentMethods: offer.paymentMethods,
    payoutDelay: offer.payoutDelay,
  };
}

function dateLabel(value: string) {
  return new Intl.DateTimeFormat("pt-BR", { dateStyle: "long", timeStyle: "short" }).format(new Date(value));
}

export function OfferForm({ productId, offerId }: { productId: string; offerId?: string }) {
  const [product, setProduct] = useState<Product | null>(null);
  const [offer, setOffer] = useState<Offer | null>(null);
  const [values, setValues] = useState<OfferInput>(blankInput);
  const [priceText, setPriceText] = useState("");
  const [errors, setErrors] = useState<OfferInputErrors>({});
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [loadFailed, setLoadFailed] = useState(false);
  const [saving, setSaving] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [publicationAction, setPublicationAction] = useState<{ label: string; url: string } | null>(null);
  const [saved, setSaved] = useState(false);
  const [simulationLoading, setSimulationLoading] = useState(false);
  const [simulationError, setSimulationError] = useState<string | null>(null);
  const [simulation, setSimulation] = useState<Awaited<ReturnType<typeof simulateOffer>> | null>(null);
  const [simulationMethod, setSimulationMethod] = useState<OfferPaymentMethod>("PIX");
  const [simulationInstallments, setSimulationInstallments] = useState(1);
  const [couponCode, setCouponCode] = useState("");
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    let active = true;
    const request = offerId ? Promise.all([getProduct(productId), getOffer(offerId)]) : getProduct(productId).then(value => [value, null] as const);
    request.then(([loadedProduct, loadedOffer]) => {
      if (!active) return;
      setProduct(loadedProduct);
      if (loadedOffer) {
        const input = inputFromOffer(loadedOffer);
        setOffer(loadedOffer);
        setValues(input);
        setPriceText(formatOfferMoney(input.priceCents));
        setSimulationMethod(input.paymentMethods[0] ?? "PIX");
        setSimulationInstallments(input.paymentMethods.includes("CARD") ? 1 : 1);
      } else {
        setValues(current => ({ ...current, cycle: loadedProduct.chargeType === "SUBSCRIPTION" ? "MONTHLY" : null }));
      }
    }).catch(error => {
      if (!active) return;
      if (error instanceof ApiRequestError && error.status === 404) setNotFound(true);
      else setLoadFailed(true);
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [offerId, productId]);

  const dirty = useMemo(() => priceText !== (offer ? formatOfferMoney(offer.priceCents) : "") || Boolean(saved), [offer, priceText, saved]);
  const immutable = (field: OfferImmutableField) => offer?.immutableFields.includes(field) ?? false;

  function change<K extends keyof OfferInput>(field: K, value: OfferInput[K]) {
    setValues(current => ({ ...current, [field]: value }));
    setErrors(current => ({ ...current, [field]: undefined }));
    setGeneralError(null);
    setSaved(false);
  }

  function toggleMethod(method: OfferPaymentMethod, checked: boolean) {
    const methods = checked ? [...new Set([...values.paymentMethods, method])] : values.paymentMethods.filter(item => item !== method);
    change("paymentMethods", methods);
  }

  function payload(): OfferInput | null {
    const priceCents = parseMoneyToCents(priceText);
    if (priceCents === null) {
      setErrors(current => ({ ...current, price: "Informe um valor válido com até duas casas decimais." }));
      return null;
    }
    const next = { ...values, priceCents };
    const validation = validateOfferInput(next, { segment: product!.segment, chargeType: product!.chargeType });
    if (Object.keys(validation).length) {
      setErrors(validation);
      setGeneralError("Revise os campos destacados.");
      return null;
    }
    return next;
  }

  async function save(event?: FormEvent) {
    event?.preventDefault();
    if (!product) return;
    const next = payload();
    if (!next) return;
    setSaving(true);
    setGeneralError(null);
    try {
      const stored = offer ? await updateOffer(offer.id, next) : await createOffer(product.id, next);
      setOffer(stored);
      setValues(inputFromOffer(stored));
      setPriceText(formatOfferMoney(stored.priceCents));
      setSaved(true);
      setSimulationMethod(stored.paymentMethods[0] ?? "PIX");
    } catch (error) {
      if (error instanceof ApiRequestError) {
        setErrors(fieldErrors(error.problem) as OfferInputErrors);
        setGeneralError(error.message);
      } else setGeneralError("Não foi possível salvar a oferta. Tente novamente.");
    } finally {
      setSaving(false);
    }
  }

  async function publish() {
    if (!offer) {
      setGeneralError("Salve o rascunho antes de publicar.");
      return;
    }
    setPublishing(true);
    setGeneralError(null);
    setPublicationAction(null);
    try {
      const result = await publishOffer(offer.id);
      setOffer(result.offer);
      if (!result.published && result.actionUrl) {
        const needsKyc = result.requiredAction === "COMPLETE_KYC";
        setGeneralError(needsKyc ? "Conclua a verificação de identidade para publicar." : "Configure o perfil fiscal para publicar.");
        setPublicationAction({ label: needsKyc ? "Continuar verificação" : "Configurar perfil fiscal", url: result.actionUrl });
      }
      else setSaved(true);
    } catch (error) {
      setGeneralError(error instanceof ApiRequestError ? error.message : "Não foi possível publicar a oferta.");
    } finally {
      setPublishing(false);
    }
  }

  async function simulate() {
    if (!offer) {
      setSimulationError("Salve o rascunho para consultar a simulação.");
      return;
    }
    setSimulationLoading(true);
    setSimulationError(null);
    try {
      setSimulation(await simulateOffer(offer.id, simulationMethod, simulationInstallments, couponCode));
    } catch (error) {
      setSimulation(null);
      setSimulationError(error instanceof ApiRequestError ? error.message : "Não foi possível consultar a simulação.");
    } finally {
      setSimulationLoading(false);
    }
  }

  async function copyLink() {
    if (!offer) return;
    const base = process.env.NEXT_PUBLIC_CHECKOUT_BASE_URL ?? "https://checkout.paysi.com.br";
    await navigator.clipboard.writeText(`${base.replace(/\/$/, "")}/checkout/${offer.slug}`);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1800);
  }

  if (loading) return <Skeleton label="Carregando formulário da oferta" />;
  if (notFound) return <EmptyState title="Oferta não encontrada" description="A oferta não existe ou não está disponível para esta conta." action={<Link className="ui-button ui-button-secondary" href={`/produtos/${productId}`}>Voltar ao produto</Link>} />;
  if (loadFailed || !product) return <Toast tone="danger">Não foi possível carregar o formulário da oferta.</Toast>;

  const cardEnabled = values.paymentMethods.includes("CARD");
  const boletoEnabled = values.paymentMethods.includes("BOLETO");
  const checkoutLink = offer ? `${(process.env.NEXT_PUBLIC_CHECKOUT_BASE_URL ?? "https://checkout.paysi.com.br").replace(/\/$/, "")}/checkout/${offer.slug}` : null;

  return <>
    <nav className="breadcrumb" aria-label="Navegação estrutural"><Link href="/produtos">Produtos</Link><span aria-hidden="true">/</span><Link href={`/produtos/${product.id}`}>{product.name}</Link><span aria-hidden="true">/</span><span aria-current="page">{offerId ? "Editar oferta" : "Nova oferta"}</span></nav>
    <header className="content-header"><div><h1>{offerId ? "Editar oferta" : "Nova oferta"}</h1><p>Configure as condições comerciais sem calcular taxas no navegador.</p></div></header>
    <form className="ui-card offer-form" onSubmit={event => void save(event)} noValidate>
      {generalError && <Toast tone="danger">{generalError}</Toast>}
      {publicationAction && <a className="ui-button ui-button-secondary publication-action" href={publicationAction.url}>{publicationAction.label}</a>}
      {saved && !generalError && <Toast tone="success">Rascunho salvo.</Toast>}
      <section aria-labelledby="commercial-title"><h2 id="commercial-title">Condições comerciais</h2><div className="offer-form-grid">
        <Campo label="Preço" value={priceText} inputMode="decimal" placeholder="20,00" error={errors.price} onChange={event => { setPriceText(event.target.value); setSaved(false); }} required />
        <Select label="Ciclo" value={values.cycle ?? ""} disabled={product.chargeType === "ONE_TIME" || immutable("CYCLE")} error={errors.cycle} onChange={event => change("cycle", (event.target.value || null) as BillingCycle | null)}><option value="">Sem ciclo</option><option value="MONTHLY">Mensal</option><option value="QUARTERLY">Trimestral</option><option value="SEMIANNUAL">Semestral</option><option value="ANNUAL">Anual</option></Select>
        <Campo label="Teste grátis (dias)" type="number" min={0} max={30} value={values.trialDays} error={errors.trialDays} onChange={event => change("trialDays", Number(event.target.value))} />
        <Campo label="Garantia (dias)" type="number" min={7} value={values.guaranteeDays} disabled={immutable("GUARANTEE")} error={errors.guaranteeDays} onChange={event => change("guaranteeDays", Number(event.target.value))} />
      </div></section>
      {immutable("CYCLE") || immutable("GUARANTEE") ? <p className="contract-lock" role="status">Ciclo e garantia ficam protegidos após a primeira cobrança confirmada.</p> : null}
      <section aria-labelledby="payment-title"><h2 id="payment-title">Pagamento e recebimento</h2><fieldset className="offer-choice-group"><legend>Meios de pagamento</legend>{(["PIX", "CARD", "BOLETO"] as OfferPaymentMethod[]).map(method => <Checkbox key={method} label={method === "PIX" ? "Pix" : method === "CARD" ? "Cartão" : "Boleto"} checked={values.paymentMethods.includes(method)} disabled={method === "BOLETO" && product.segment !== "SAAS"} onChange={event => toggleMethod(method, event.target.checked)} />)}{errors.paymentMethods && <small className="ui-error">{errors.paymentMethods}</small>}</fieldset>
        <div className="offer-form-grid"><Campo label="Máximo de parcelas" type="number" min={1} max={12} disabled={!cardEnabled} value={values.maxInstallments} error={errors.maxInstallments} onChange={event => change("maxInstallments", Number(event.target.value))} /><Select label="Prazo de recebimento" value={values.payoutDelay} onChange={event => change("payoutDelay", event.target.value as OfferPayoutDelay)}><option value="D32">D+32</option><option value="D15">D+15</option><option value="D7">D+7</option><option value="D2">D+2</option></Select><Campo label="Vencimento do boleto (dias)" type="number" min={1} max={15} disabled={!boletoEnabled} value={values.boletoDueDays} error={errors.boletoDueDays} onChange={event => change("boletoDueDays", Number(event.target.value))} /><Campo label="Antecedência do boleto (dias)" type="number" min={3} max={10} disabled={!boletoEnabled} value={values.boletoAdvanceDays} error={errors.boletoAdvanceDays} onChange={event => change("boletoAdvanceDays", Number(event.target.value))} /></div>
        <Checkbox label="Exigir cartão no teste grátis" checked={values.trialRequiresCard} disabled={product.segment !== "SAAS"} onChange={event => change("trialRequiresCard", event.target.checked)} />
      </section>
      <section className="offer-simulation" aria-labelledby="simulation-title"><div className="section-heading"><div><h2 id="simulation-title">Simulação</h2><p>Os valores abaixo vêm do backend e não alteram o cupom.</p></div><Botao type="button" variant="secondary" disabled={simulationLoading || !offer} onClick={() => void simulate()}>{simulationLoading ? "Consultando…" : "Consultar simulação"}</Botao></div><div className="offer-form-grid"><Select label="Método" value={simulationMethod} onChange={event => setSimulationMethod(event.target.value as OfferPaymentMethod)}>{values.paymentMethods.map(method => <option key={method} value={method}>{method}</option>)}</Select><Campo label="Parcelas da simulação" type="number" min={1} max={values.maxInstallments} disabled={!cardEnabled || simulationMethod !== "CARD"} value={simulationInstallments} onChange={event => setSimulationInstallments(Number(event.target.value))} /><Campo label="Cupom (opcional)" value={couponCode} maxLength={32} onChange={event => setCouponCode(event.target.value.toUpperCase())} /></div>{simulationError && <Toast tone="danger">{simulationError}</Toast>}{simulation && <dl className="simulation-result"><div><dt>Preço</dt><dd>{formatOfferMoney(simulation.grossCents)}</dd></div><div><dt>Desconto</dt><dd>{formatOfferMoney(simulation.discountCents)}</dd></div><div><dt>Pago</dt><dd>{formatOfferMoney(simulation.paidCents)}</dd></div><div><dt>Taxa da plataforma</dt><dd>{formatOfferMoney(simulation.platformFeeCents)}</dd></div><div><dt>Você recebe</dt><dd>{formatOfferMoney(simulation.sellerCents)}</dd></div><div><dt>Disponível em</dt><dd>{dateLabel(simulation.availableAt)}</dd></div></dl>}</section>
      <div className="ui-actions product-form-actions"><Botao type="submit" disabled={saving || publishing}>{saving ? "Salvando…" : "Salvar rascunho"}</Botao><Botao type="button" disabled={saving || publishing || !offer} onClick={() => void publish()}>{publishing ? "Publicando…" : "Publicar oferta"}</Botao><Link className="ui-button ui-button-secondary" href={`/produtos/${product.id}`}>Cancelar</Link>{dirty && !saved && <span className="unsaved-indicator" role="status">Alterações não salvas</span>}</div>
      {offer?.status === "PUBLISHED" && checkoutLink && <div className="offer-published" role="status"><strong>Checkout publicado</strong><a href={checkoutLink} target="_blank" rel="noreferrer">{checkoutLink}</a><Botao type="button" variant="secondary" onClick={() => void copyLink()}>{copied ? "Link copiado" : "Copiar link"}</Botao></div>}
    </form>
  </>;
}
