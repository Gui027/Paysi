"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { ReactNode, useEffect, useState } from "react";
import { ApiRequestError } from "../../../../lib/api";
import { archiveProduct, getProduct, Product, productChargeTypeLabel, productSegmentLabel, productStatusLabel, updateProduct, validateProductInput } from "../../../../lib/produtos";
import {
  BillingCycle, createOffer, formatOfferMoney, listOffers, Offer, OfferInput, OfferInputErrors, OfferPaymentMethod, parseMoneyToCents,
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
  };
}

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
      const current = offers.find(item => item.status !== "ARCHIVED") ?? null;
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

  function selectAba(next: Aba) {
    router.replace(next === "geral" ? `/produtos/${productId}` : `/produtos/${productId}?aba=${next}`, { scroll: false });
  }

  async function save(): Promise<boolean> {
    if (!product) return false;
    const cents = parseMoneyToCents(price);
    const next: OfferInput = { ...values, priceCents: cents ?? 0 };
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
      setValues(offerInput(stored));
      setPrice(priceText(stored.priceCents));
      setMessage({ tone: "success", text: "Produto salvo." });
      return true;
    } catch (error) {
      setMessage({ tone: "danger", text: error instanceof ApiRequestError ? error.message : "Não foi possível salvar o produto. Tente novamente." });
      return false;
    } finally {
      setSaving(false);
    }
  }

  async function publish() {
    if (!offer) { setMessage({ tone: "danger", text: "Salve o produto antes de publicar o checkout." }); return; }
    setPublishing(true);
    setMessage(null);
    setNextStep(null);
    try {
      const result = await publishOffer(offer.id);
      setOffer(result.offer);
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

    {message && <Toast tone={message.tone}>{message.text}{nextStep && <> <Link href={nextStep.url}>{nextStep.label}</Link></>}</Toast>}

    <div role="tabpanel" id={`painel-${aba}`} aria-labelledby={`aba-${aba}`}>
      {aba === "geral" && <>
        <Secao titulo="Produto" texto="Nome e descrição que o comprador vê no checkout.">
          <label className="pe-field"><span>Nome do produto</span><input value={name} maxLength={120} aria-invalid={Boolean(errors.name)} onChange={event => { setName(event.target.value); setErrors(current => ({ ...current, name: undefined })); }} />{errors.name && <small className="pe-error">{errors.name}</small>}</label>
          <label className="pe-field"><span>Descrição</span><textarea rows={4} maxLength={2000} value={description} onChange={event => setDescription(event.target.value)} /><small className="pe-hint">{description.length}/2.000</small></label>
          <dl className="pe-facts"><div><dt>Tipo de pagamento</dt><dd>{productChargeTypeLabel[product.chargeType]}</dd></div><div><dt>Tipo de produto</dt><dd>{productSegmentLabel[product.segment]}</dd></div><div><dt>Status</dt><dd>{productStatusLabel[product.status]}</dd></div></dl>
        </Secao>
        <Secao titulo="Preço" texto={subscription ? "Valor cobrado a cada ciclo." : undefined}>
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
        <p className="pe-hint">Cada oferta tem um checkout. Publique para liberar o link de compra.</p>
        {offer ? <table className="prod-table">
          <thead><tr><th scope="col">Nome</th><th scope="col">Preço</th><th scope="col">Status</th><th scope="col"><span className="sr-only">Ações</span></th></tr></thead>
          <tbody><tr>
            <td><span className="prod-name">Checkout A</span> <span className="pe-badge">Padrão</span></td>
            <td className="prod-muted">{formatOfferMoney(offer.priceCents)}</td>
            <td><span className={`pe-pill ${published ? "pe-pill-on" : ""}`}>{published ? "Publicado" : "Rascunho"}</span></td>
            <td className="prod-actions">{published ? <button type="button" className="ui-button ui-button-secondary" onClick={() => copiar(link)}>{copied ? "Link copiado" : "Copiar link"}</button> : <button type="button" className="ui-button ui-button-primary" disabled={publishing} onClick={() => void publish()}>{publishing ? "Publicando…" : "Publicar"}</button>}</td>
          </tr></tbody>
        </table> : <p className="pe-empty">Salve o produto para criar o checkout.</p>}
      </div>}

      {aba === "links" && <div className="pe-panel">
        <p className="pe-hint">Links para divulgar este produto.</p>
        {offer && published && link ? <table className="prod-table">
          <thead><tr><th scope="col">Nome do link</th><th scope="col">URL</th><th scope="col">Tipo</th><th scope="col">Preço</th><th scope="col"><span className="sr-only">Ações</span></th></tr></thead>
          <tbody><tr>
            <td><span className="prod-name">{product.name}</span></td>
            <td><input className="pe-url" readOnly aria-label="URL do checkout" value={link} onFocus={event => event.currentTarget.select()} /></td>
            <td><span className="pe-pill pe-pill-blue">Checkout</span></td>
            <td className="prod-muted">{formatOfferMoney(offer.priceCents)}</td>
            <td className="prod-actions"><button type="button" className="ui-button ui-button-secondary" onClick={() => copiar(link)}>{copied ? "Copiado" : "Copiar"}</button></td>
          </tr></tbody>
        </table> : <p className="pe-empty">Os links aparecem aqui depois que o checkout for publicado. <button type="button" className="pe-linkbtn" onClick={() => selectAba("checkout")}>Ir para Checkout</button></p>}
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
