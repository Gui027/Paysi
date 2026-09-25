"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { ReactNode, useEffect, useState } from "react";
import { ApiRequestError } from "../../../../lib/api";
import { archiveProduct, getProduct, Product, productChargeTypeLabel, productSegmentLabel, productStatusLabel, updateProduct, validateProductInput } from "../../../../lib/produtos";
import {
  BillingCycle, createOffer, duplicateOffer, formatOfferMoney, listOffers, Offer, OfferInput, OfferInputErrors, OfferPaymentMethod, parseMoneyToCents,
  publishOffer, updateOffer, validateOfferInput,
} from "../../../../lib/ofertas";
import { AffiliateProgramInput, AffiliationRecurrence, getAffiliateProgram, parseCommissionPercent, recurrenceLabel, updateAffiliateProgram } from "../../../../lib/afiliados";
import { EmptyState, Skeleton, Toast } from "../../../../components/ui";

const defaultProgram: AffiliateProgramInput = { commissionBps: 3000, recurrence: "FIRST_CHARGE", autoApprove: false, supportEmail: null, description: null };
const formatBps = (bps: number) => (bps / 100).toString().replace(".", ",");
const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

type Aba = "geral" | "configuracoes" | "checkout" | "afiliados" | "links";
const abas: readonly [Aba, string][] = [["geral", "Geral"], ["configuracoes", "Configurações"], ["checkout", "Checkout"], ["afiliados", "Afiliados"], ["links", "Links"]];
const cycleLabel: Record<BillingCycle, string> = { MONTHLY: "Mensal", QUARTERLY: "Trimestral", SEMIANNUAL: "Semestral", ANNUAL: "Anual" };
const methodLabel: Record<OfferPaymentMethod, string> = { PIX: "Pix", CARD: "Cartão de crédito", BOLETO: "Boleto" };

const blankOffer: OfferInput = {
  priceCents: 0, cycle: null, trialDays: 0, trialRequiresCard: true, guaranteeDays: 7, maxInstallments: 1,
  boletoDueDays: 3, boletoAdvanceDays: 5, paymentMethods: ["PIX", "CARD"], payoutDelay: "D32",
};

function offerInput(offer: Offer): OfferInput {
  return {
    priceCents: offer.priceCents, cycle: offer.cycle, trialDays: offer.trialDays, trialRequiresCard: offer.trialRequiresCard,
    guaranteeDays: offer.guaranteeDays, maxInstallments: offer.maxInstallments, boletoDueDays: offer.boletoDueDays,
    boletoAdvanceDays: offer.boletoAdvanceDays, paymentMethods: offer.paymentMethods, payoutDelay: offer.payoutDelay,
    name: offer.name,
  };
}

const offerLabel = (offer: Offer, offers: Offer[]) => offer.name?.trim() || `Oferta ${offers.findIndex(item => item.id === offer.id) + 1}`;
const upsert = (list: Offer[], offer: Offer) => list.some(item => item.id === offer.id) ? list.map(item => item.id === offer.id ? offer : item) : [...list, offer];
const priceText = (cents: number) => (cents / 100).toFixed(2).replace(".", ",");
const checkoutBase = () => (process.env.NEXT_PUBLIC_CHECKOUT_BASE_URL ?? "https://checkout.paysi.com.br").replace(/\/$/, "");

function Secao({ titulo, texto, children }: { titulo: string; texto?: ReactNode; children: ReactNode }) {
  return <section className="pe-section"><div className="pe-section-intro"><h2>{titulo}</h2>{texto && <p>{texto}</p>}</div><div className="pe-card">{children}</div></section>;
}

function Chave({ label, checked, disabled, onChange }: { label: string; checked: boolean; disabled?: boolean; onChange: (checked: boolean) => void }) {
  return <label className="pe-switch"><input type="checkbox" role="switch" checked={checked} disabled={disabled} onChange={event => onChange(event.target.checked)} /><span className="pe-track" aria-hidden="true" /><span>{label}</span></label>;
}

export function ProdutoDetalhe({ productId }: { productId: string }) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const aba = (abas.find(([id]) => id === searchParams.get("aba"))?.[0] ?? "geral") as Aba;

  const [product, setProduct] = useState<Product | null>(null);
  const [offer, setOffer] = useState<Offer | null>(null);
  const [offers, setOffers] = useState<Offer[]>([]);
  const [offerName, setOfferName] = useState("");
  const [duplicating, setDuplicating] = useState(false);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [affiliation, setAffiliation] = useState(false);
  const [values, setValues] = useState<OfferInput>(blankOffer);
  const [price, setPrice] = useState("");
  const [errors, setErrors] = useState<OfferInputErrors & { name?: string; description?: string }>({});
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [loadFailed, setLoadFailed] = useState(false);
  const [saving, setSaving] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [message, setMessage] = useState<{ tone: "success" | "danger"; text: string } | null>(null);
  const [nextStep, setNextStep] = useState<{ label: string; url: string } | null>(null);
  const [copied, setCopied] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [program, setProgram] = useState<AffiliateProgramInput>(defaultProgram);
  const [commission, setCommission] = useState(formatBps(defaultProgram.commissionBps));
  const [programErrors, setProgramErrors] = useState<{ commission?: string; supportEmail?: string }>({});

  function changeProgram(patch: Partial<AffiliateProgramInput>) {
    setProgram(current => ({ ...current, ...patch }));
    setProgramErrors(current => ({ ...current, supportEmail: undefined }));
    setMessage(null);
  }

  useEffect(() => {
    let active = true;
    Promise.all([getProduct(productId), listOffers(productId), getAffiliateProgram(productId).catch(() => null)]).then(([loaded, offers, loadedProgram]) => {
      if (!active) return;
      if (loadedProgram) { setProgram(loadedProgram); setCommission(formatBps(loadedProgram.commissionBps)); }
      const active_ = offers.filter(item => item.status !== "ARCHIVED").sort((a, b) => a.createdAt.localeCompare(b.createdAt));
      const wanted = searchParams.get("oferta");
      const current = active_.find(item => item.id === wanted) ?? active_[0] ?? null;
      setOffers(active_);
      setOfferName(current?.name ?? "");
      setProduct(loaded);
      setName(loaded.name);
      setDescription(loaded.description ?? "");
      setAffiliation(loaded.affiliationEnabled);
      setOffer(current);
      setValues(current ? offerInput(current) : { ...blankOffer, cycle: loaded.chargeType === "SUBSCRIPTION" ? "MONTHLY" : null });
      setPrice(current ? priceText(current.priceCents) : "");
    }).catch(error => {
      if (!active) return;
      if (error instanceof ApiRequestError && error.status === 404) setNotFound(true);
      else setLoadFailed(true);
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [productId]);

  function change<K extends keyof OfferInput>(field: K, value: OfferInput[K]) {
    setValues(current => ({ ...current, [field]: value }));
    setErrors(current => ({ ...current, [field]: undefined }));
    setMessage(null);
  }

  function toggleMethod(method: OfferPaymentMethod, checked: boolean) {
    change("paymentMethods", checked ? [...new Set([...values.paymentMethods, method])] : values.paymentMethods.filter(item => item !== method));
  }

  function urlFor(aba: Aba, offerId: string | null) {
    const query = new URLSearchParams();
    if (aba !== "geral") query.set("aba", aba);
    if (offerId && offers.length > 1) query.set("oferta", offerId);
    const text = query.toString();
    return text ? `/produtos/${productId}?${text}` : `/produtos/${productId}`;
  }

  function selectAba(next: Aba) {
    router.replace(urlFor(next, offer?.id ?? null), { scroll: false });
  }

  // Troca a oferta em edição: os campos de preço e configurações passam a ser os dela.
  function selectOffer(next: Offer, toAba: Aba = aba) {
    setOffer(next);
    setValues(offerInput(next));
    setPrice(priceText(next.priceCents));
    setOfferName(next.name ?? "");
    setErrors({});
    setMessage(null);
    router.replace(`/produtos/${productId}?${new URLSearchParams({ ...(toAba !== "geral" ? { aba: toAba } : {}), oferta: next.id })}`, { scroll: false });
  }

  async function duplicate(source: Offer) {
    setDuplicating(true);
    setMessage(null);
    try {
      const copy = await duplicateOffer(source.id);
      setOffers(current => upsert(current, copy));
      selectOffer(copy, "geral");
      setMessage({ tone: "success", text: "Oferta duplicada. Ajuste o nome e o preço e salve." });
    } catch (error) {
      setMessage({ tone: "danger", text: error instanceof ApiRequestError ? error.message : "Não foi possível duplicar a oferta." });
    } finally {
      setDuplicating(false);
    }
  }

  async function save(): Promise<boolean> {
    if (!product) return false;
    const cents = parseMoneyToCents(price);
    const next: OfferInput = { ...values, priceCents: cents ?? 0, name: offerName.trim() || null };
    const productErrors = validateProductInput({ name, description: description || null, segment: product.segment, chargeType: product.chargeType, affiliationEnabled: affiliation });
    const offerErrors = validateOfferInput(next, { segment: product.segment, chargeType: product.chargeType });
    if (cents === null) offerErrors.price = "Informe um valor válido com até duas casas decimais.";
    const all = { ...productErrors, ...offerErrors };
    const commissionBps = parseCommissionPercent(commission);
    const nextProgramErrors: { commission?: string; supportEmail?: string } = {};
    if (affiliation && commissionBps === null) nextProgramErrors.commission = "Informe uma comissão entre 0 e 50%.";
    if (affiliation && program.supportEmail?.trim() && !emailPattern.test(program.supportEmail.trim())) nextProgramErrors.supportEmail = "Informe um e-mail válido.";
    setErrors(all);
    setProgramErrors(nextProgramErrors);
    if (Object.keys(nextProgramErrors).length) {
      setMessage({ tone: "danger", text: "Revise os campos destacados." });
      selectAba("afiliados");
      return false;
    }
    if (Object.keys(all).length) {
      setMessage({ tone: "danger", text: "Revise os campos destacados." });
      if (productErrors.name || productErrors.description || offerErrors.price) selectAba("geral");
      return false;
    }
    setSaving(true);
    setMessage(null);
    try {
      const updated = await updateProduct(product.id, { name, description: description || null, segment: product.segment, chargeType: product.chargeType, affiliationEnabled: affiliation });
      const stored = offer ? await updateOffer(offer.id, next) : await createOffer(product.id, next);
      if (affiliation && commissionBps !== null) {
        const savedProgram = await updateAffiliateProgram(product.id, { ...program, commissionBps, supportEmail: program.supportEmail?.trim() || null, description: program.description?.trim() || null });
        setProgram(savedProgram);
        setCommission(formatBps(savedProgram.commissionBps));
      }
      setProduct(updated);
      setOffer(stored);
      setOffers(current => upsert(current, stored));
      setValues(offerInput(stored));
      setPrice(priceText(stored.priceCents));
      setOfferName(stored.name ?? "");
      setMessage({ tone: "success", text: "Produto salvo." });
      return true;
    } catch (error) {
      setMessage({ tone: "danger", text: error instanceof ApiRequestError ? error.message : "Não foi possível salvar o produto. Tente novamente." });
      return false;
    } finally {
      setSaving(false);
    }
  }

  async function publish(target: Offer | null = offer) {
    if (!target) { setMessage({ tone: "danger", text: "Salve o produto antes de publicar o checkout." }); return; }
    setPublishing(true);
    setMessage(null);
    setNextStep(null);
    try {
      const result = await publishOffer(target.id);
      setOffers(current => upsert(current, result.offer));
      if (offer?.id === result.offer.id) setOffer(result.offer);
      if (result.published) setMessage({ tone: "success", text: "Checkout publicado." });
      else {
        // actionUrl aponta para fora do painel; levamos o vendedor à tela interna certa e voltamos (?next=).
        const needsKyc = result.requiredAction === "COMPLETE_KYC";
        const back = encodeURIComponent(`/produtos/${productId}?aba=checkout`);
        setMessage({ tone: "danger", text: needsKyc ? "Conclua a verificação de identidade para publicar." : "Configure o perfil fiscal para publicar." });
        setNextStep({ label: needsKyc ? "Continuar verificação" : "Configurar perfil fiscal", url: `${needsKyc ? "/verificacao" : "/perfil-fiscal"}?next=${back}` });
      }
    } catch (error) {
      setMessage({ tone: "danger", text: error instanceof ApiRequestError ? error.message : "Não foi possível publicar o checkout." });
    } finally {
      setPublishing(false);
    }
  }

  function copiar(text: string | null) {
    if (!text) return;
    void navigator.clipboard.writeText(text).then(() => { setCopied(true); window.setTimeout(() => setCopied(false), 1800); });
  }

  async function remove() {
    try {
      await archiveProduct(productId);
      router.push("/produtos");
    } catch {
      setConfirmDelete(false);
      setMessage({ tone: "danger", text: "Não foi possível excluir o produto." });
    }
  }

  if (loading) return <Skeleton label="Carregando produto" />;
  if (notFound) return <EmptyState title="Produto não encontrado" description="O produto não existe ou não está disponível para esta conta." action={<Link className="ui-button ui-button-secondary" href="/produtos">Voltar aos produtos</Link>} />;
  if (loadFailed || !product) return <Toast tone="danger">Não foi possível carregar o produto. <Link href="/produtos">Voltar aos produtos</Link></Toast>;

  const cardOn = values.paymentMethods.includes("CARD");
  const boletoOn = values.paymentMethods.includes("BOLETO");
  const subscription = product.chargeType === "SUBSCRIPTION";
  const link = offer ? `${checkoutBase()}/checkout/${offer.slug}` : null;
  const inviteLink = `${typeof window === "undefined" ? "" : window.location.origin}/afiliar/${product.id}`;
  const published = offer?.status === "PUBLISHED";
  const lockedContract = offer?.immutableFields ?? [];

  return <div className="pe">
    <header className="pe-head">
      <div className="pe-title"><Link href="/produtos" aria-label="Voltar aos produtos" className="pe-back">←</Link><h1>{product.name}</h1></div>
      <button type="button" className="ui-button ui-button-primary" disabled={saving} onClick={() => void save()}>{saving ? "Salvando…" : "Salvar produto"}</button>
    </header>

    <div className="pe-tabs" role="tablist" aria-label="Seções do produto">
      {abas.map(([id, label]) => <button key={id} type="button" role="tab" id={`aba-${id}`} aria-selected={aba === id} aria-controls={`painel-${id}`} onClick={() => selectAba(id)}>{label}</button>)}
    </div>

    {offers.length > 1 && (aba === "geral" || aba === "configuracoes") && offer && <label className="pe-switcher"><span>Oferta em edição</span><select value={offer.id} onChange={event => { const next = offers.find(item => item.id === event.target.value); if (next) selectOffer(next); }}>{offers.map(item => <option key={item.id} value={item.id}>{offerLabel(item, offers)} · {formatOfferMoney(item.priceCents)}</option>)}</select></label>}

    {message && <Toast tone={message.tone}>{message.text}{nextStep && <> <Link href={nextStep.url}>{nextStep.label}</Link></>}</Toast>}

    <div role="tabpanel" id={`painel-${aba}`} aria-labelledby={`aba-${aba}`}>
      {aba === "geral" && <>
        <Secao titulo="Produto" texto="Nome e descrição que o comprador vê no checkout.">
          <label className="pe-field"><span>Nome do produto</span><input value={name} maxLength={120} aria-invalid={Boolean(errors.name)} onChange={event => { setName(event.target.value); setErrors(current => ({ ...current, name: undefined })); }} />{errors.name && <small className="pe-error">{errors.name}</small>}</label>
          <label className="pe-field"><span>Descrição</span><textarea rows={4} maxLength={2000} value={description} onChange={event => setDescription(event.target.value)} /><small className="pe-hint">{description.length}/2.000</small></label>
          <dl className="pe-facts"><div><dt>Tipo de pagamento</dt><dd>{productChargeTypeLabel[product.chargeType]}</dd></div><div><dt>Tipo de produto</dt><dd>{productSegmentLabel[product.segment]}</dd></div><div><dt>Status</dt><dd>{productStatusLabel[product.status]}</dd></div></dl>
        </Secao>
        <Secao titulo="Preço" texto={subscription ? "Valor cobrado a cada ciclo." : "Cada oferta tem o seu preço e o seu link."}>
          <label className="pe-field"><span>Nome da oferta</span><input value={offerName} maxLength={60} placeholder="Ex.: Plano Pro" aria-invalid={Boolean(errors.name)} onChange={event => { setOfferName(event.target.value); setErrors(current => ({ ...current, name: undefined })); }} />{errors.name && <small className="pe-error">{errors.name}</small>}<small className="pe-hint">Só você vê este nome; ele ajuda a distinguir as ofertas.</small></label>
          <label className="pe-field"><span>Preço</span><span className="pe-money"><span aria-hidden="true">R$</span><input inputMode="decimal" placeholder="0,00" aria-label="Preço em reais" value={price} aria-invalid={Boolean(errors.price)} onChange={event => { setPrice(event.target.value); setErrors(current => ({ ...current, price: undefined })); }} /></span>{errors.price && <small className="pe-error">{errors.price}</small>}</label>
          {subscription && <label className="pe-field"><span>Cobrança</span><select value={values.cycle ?? "MONTHLY"} disabled={lockedContract.includes("CYCLE")} onChange={event => change("cycle", event.target.value as BillingCycle)}>{Object.entries(cycleLabel).map(([value, text]) => <option key={value} value={value}>{text}</option>)}</select>{errors.cycle && <small className="pe-error">{errors.cycle}</small>}</label>}
        </Secao>
      </>}

      {aba === "configuracoes" && <>
        <Secao titulo="Pagamento" texto="Escolha como o comprador pode pagar.">
          <fieldset className="pe-fieldset"><legend>Métodos de pagamento</legend>
            {(["PIX", "CARD", "BOLETO"] as OfferPaymentMethod[]).map(method => <Chave key={method} label={methodLabel[method]} checked={values.paymentMethods.includes(method)} disabled={method === "BOLETO" && product.segment !== "SAAS"} onChange={checked => toggleMethod(method, checked)} />)}
            {product.segment !== "SAAS" && <small className="pe-hint">Boleto está disponível apenas para produtos de software (SaaS).</small>}
            {errors.paymentMethods && <small className="pe-error">{errors.paymentMethods}</small>}
          </fieldset>
          <label className="pe-field"><span>Parcelamento</span><select disabled={!cardOn} value={values.maxInstallments} onChange={event => change("maxInstallments", Number(event.target.value))}>{Array.from({ length: 12 }, (_, index) => index + 1).map(n => <option key={n} value={n}>{n === 1 ? "À vista" : `Até ${n}x`}</option>)}</select>{errors.maxInstallments && <small className="pe-error">{errors.maxInstallments}</small>}</label>
          {boletoOn && <label className="pe-field"><span>Validade do boleto</span><span className="pe-inline"><input type="number" min={1} max={15} value={values.boletoDueDays} onChange={event => change("boletoDueDays", Number(event.target.value))} /><span>dias corridos</span></span>{errors.boletoDueDays && <small className="pe-error">{errors.boletoDueDays}</small>}</label>}
        </Secao>
        <Secao titulo="Garantia" texto="Prazo em que o comprador pode pedir reembolso.">
          <label className="pe-field"><span>Garantia</span><span className="pe-inline"><input type="number" min={7} value={values.guaranteeDays} disabled={lockedContract.includes("GUARANTEE")} onChange={event => change("guaranteeDays", Number(event.target.value))} /><span>dias (mínimo 7)</span></span>{errors.guaranteeDays && <small className="pe-error">{errors.guaranteeDays}</small>}</label>
          {lockedContract.length > 0 && <small className="pe-hint">Ciclo e garantia ficam protegidos após a primeira cobrança confirmada.</small>}
        </Secao>
        {subscription && <Secao titulo="Teste grátis" texto="Deixe em 0 para cobrar desde o início.">
          <label className="pe-field"><span>Período de teste</span><span className="pe-inline"><input type="number" min={0} max={30} value={values.trialDays} onChange={event => change("trialDays", Number(event.target.value))} /><span>dias</span></span>{errors.trialDays && <small className="pe-error">{errors.trialDays}</small>}</label>
          {product.segment === "SAAS" && <Chave label="Exigir cartão para iniciar o teste" checked={values.trialRequiresCard} onChange={checked => change("trialRequiresCard", checked)} />}
        </Secao>}
        <Secao titulo="Cupons de desconto" texto="Crie códigos promocionais para este e outros produtos."><Link className="ui-button ui-button-secondary pe-fit" href="/cupons">Gerenciar cupons</Link></Secao>
      </>}

      {aba === "checkout" && <div className="pe-panel">
        <div className="pe-panel-head">
          <p className="pe-hint">Cada oferta tem um preço, um checkout e um link. Publique para liberar o link de compra.</p>
          {offer && <button type="button" className="ui-button ui-button-secondary" disabled={duplicating} onClick={() => void duplicate(offer)}>{duplicating ? "Criando…" : "Nova oferta"}</button>}
        </div>
        {offers.length > 0 ? <table className="prod-table">
          <thead><tr><th scope="col">Nome</th><th scope="col">Preço</th><th scope="col">Status</th><th scope="col"><span className="sr-only">Ações</span></th></tr></thead>
          <tbody>{offers.map((item, index) => {
            const itemPublished = item.status === "PUBLISHED";
            return <tr key={item.id}>
              <td><span className="prod-name">{offerLabel(item, offers)}</span>{index === 0 && <span className="pe-badge">Padrão</span>}</td>
              <td className="prod-muted">{formatOfferMoney(item.priceCents)}</td>
              <td><span className={`pe-pill ${itemPublished ? "pe-pill-on" : ""}`}>{itemPublished ? "Publicado" : "Rascunho"}</span></td>
              <td className="prod-actions"><div className="pe-row-actions">
                <button type="button" className="ui-button ui-button-secondary" aria-label={`Editar ${offerLabel(item, offers)}`} onClick={() => selectOffer(item, "geral")}>Editar</button>
                <Link className="ui-button ui-button-secondary" href={`/aparencia/${item.id}`} aria-label={`Personalizar ${offerLabel(item, offers)}`}>Personalizar</Link>
                <button type="button" className="ui-button ui-button-secondary" disabled={duplicating} aria-label={`Duplicar ${offerLabel(item, offers)}`} onClick={() => void duplicate(item)}>Duplicar</button>
                {itemPublished ? <button type="button" className="ui-button ui-button-secondary" aria-label={`Copiar link de ${offerLabel(item, offers)}`} onClick={() => copiar(`${checkoutBase()}/checkout/${item.slug}`)}>{copied ? "Link copiado" : "Copiar link"}</button> : <button type="button" className="ui-button ui-button-primary" disabled={publishing} aria-label={`Publicar ${offerLabel(item, offers)}`} onClick={() => void publish(item)}>{publishing ? "Publicando…" : "Publicar"}</button>}
              </div></td>
            </tr>;
          })}</tbody>
        </table> : <p className="pe-empty">Salve o produto para criar o checkout.</p>}
      </div>}

      {aba === "links" && <div className="pe-panel">
        <p className="pe-hint">Links para divulgar este produto. Cada oferta publicada tem o seu.</p>
        {offers.filter(item => item.status === "PUBLISHED").length > 0 ? <table className="prod-table">
          <thead><tr><th scope="col">Oferta</th><th scope="col">URL</th><th scope="col">Tipo</th><th scope="col">Preço</th><th scope="col"><span className="sr-only">Ações</span></th></tr></thead>
          <tbody>{offers.filter(item => item.status === "PUBLISHED").map(item => {
            const itemLink = `${checkoutBase()}/checkout/${item.slug}`;
            return <tr key={item.id}>
              <td><span className="prod-name">{offerLabel(item, offers)}</span></td>
              <td><input className="pe-url" readOnly aria-label={`URL do checkout de ${offerLabel(item, offers)}`} value={itemLink} onFocus={event => event.currentTarget.select()} /></td>
              <td><span className="pe-pill pe-pill-blue">Checkout</span></td>
              <td className="prod-muted">{formatOfferMoney(item.priceCents)}</td>
              <td className="prod-actions"><button type="button" className="ui-button ui-button-secondary" onClick={() => copiar(itemLink)}>{copied ? "Copiado" : "Copiar"}</button></td>
            </tr>;
          })}</tbody>
        </table> : <p className="pe-empty">Os links aparecem aqui depois que um checkout for publicado. <button type="button" className="pe-linkbtn" onClick={() => selectAba("checkout")}>Ir para Checkout</button></p>}
      </div>}

      {aba === "afiliados" && <>
        <p className="pe-tip">Você gerencia os seus afiliados pelo menu <Link href="/afiliados">Afiliados</Link>: aprovar pedidos, definir a comissão e encerrar afiliações.</p>
        <Secao titulo="Configurações" texto={<>Aprenda mais sobre os <Link href="/afiliados">afiliados</Link>.</>}>
          <Chave label="Habilitar programa de afiliados" checked={affiliation} onChange={checked => { setAffiliation(checked); setMessage(null); }} />
          {affiliation && <>
            <Chave label="Aprovar cada solicitação de afiliação manualmente" checked={!program.autoApprove} onChange={checked => changeProgram({ autoApprove: !checked })} />
            <label className="pe-field"><span>E-mail de suporte para afiliados</span><input type="email" placeholder="suporte@seunegocio.com" value={program.supportEmail ?? ""} aria-invalid={Boolean(programErrors.supportEmail)} onChange={event => changeProgram({ supportEmail: event.target.value })} />{programErrors.supportEmail && <small className="pe-error">{programErrors.supportEmail}</small>}</label>
            <label className="pe-field"><span>Descrição para afiliados</span><textarea rows={4} maxLength={1000} placeholder="Conte o que o afiliado precisa saber para divulgar bem o produto" value={program.description ?? ""} onChange={event => changeProgram({ description: event.target.value })} /><small className="pe-hint">{(program.description ?? "").length}/1.000. Aparece na vitrine de afiliados.</small></label>
            <label className="pe-field"><span>Comissão</span><span className="pe-inline"><input inputMode="decimal" aria-label="Comissão em porcentagem" value={commission} aria-invalid={Boolean(programErrors.commission)} onChange={event => { setCommission(event.target.value); setProgramErrors(current => ({ ...current, commission: undefined })); setMessage(null); }} /><span>% (de 0 a 50)</span></span>{programErrors.commission && <small className="pe-error">{programErrors.commission}</small>}</label>
            {subscription && <label className="pe-field"><span>Recorrência</span><select value={program.recurrence} onChange={event => changeProgram({ recurrence: event.target.value as AffiliationRecurrence })}>{Object.entries(recurrenceLabel).map(([value, text]) => <option key={value} value={value}>{text}</option>)}</select></label>}
            <small className="pe-hint">{program.autoApprove ? "Quem pedir afiliação é aprovado na hora, com a comissão acima." : "Você aprova cada pedido em Afiliados e pode ajustar a comissão de cada um."} A comissão de quem já é afiliado não muda.</small>
          </>}
          {!affiliation && <small className="pe-hint">Clique em “Salvar produto” para aplicar a mudança.</small>}
        </Secao>
        {affiliation && <Secao titulo="Convidar afiliados" texto={<>Aprenda mais sobre <Link href="/afiliados">convidar afiliados</Link>.</>}>
          <div className="pe-field"><span>Copiar link de convite de afiliado</span>
            <div className="pe-link"><input className="pe-url" readOnly aria-label="Link de convite de afiliado" value={inviteLink} onFocus={event => event.currentTarget.select()} /><button type="button" className="ui-button ui-button-primary" onClick={() => copiar(inviteLink)}>{copied ? "Copiado" : "Copiar"}</button></div>
            <small className="pe-hint">Compartilhe este link para convidar afiliados. {product.affiliationEnabled ? "O produto só aparece na vitrine e aceita pedidos depois de publicado." : "Salve o produto para ativar o programa antes de compartilhar."}</small>
          </div>
        </Secao>}
      </>}
    </div>

    <footer className="pe-foot">
      {confirmDelete ? <span className="pe-confirm" role="alert">Excluir “{product.name}”? <button type="button" className="ui-button ui-button-danger" onClick={() => void remove()}>Sim, excluir</button> <button type="button" className="ui-button ui-button-secondary" onClick={() => setConfirmDelete(false)}>Cancelar</button></span>
        : <button type="button" className="ui-button ui-button-danger" onClick={() => setConfirmDelete(true)}>Excluir produto</button>}
      <button type="button" className="ui-button ui-button-primary" disabled={saving} onClick={() => void save()}>{saving ? "Salvando…" : "Salvar produto"}</button>
    </footer>
  </div>;
}
