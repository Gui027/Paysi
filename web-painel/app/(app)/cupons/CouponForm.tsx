"use client";

import Link from "next/link";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { Botao, Campo, Checkbox, EmptyState, Select, Skeleton, Toast } from "../../../components/ui";
import { ApiRequestError, fieldErrors } from "../../../lib/api";
import {
  Coupon,
  CouponInput,
  CouponInputErrors,
  CouponKind,
  createCoupon,
  formatBps,
  formatFixedDiscount,
  fromDatetimeLocal,
  getCoupon,
  listOfferOptions,
  OfferOption,
  parseFixedDiscount,
  parsePercentToBps,
  toDatetimeLocal,
  updateCoupon,
  validateCouponInput,
} from "../../../lib/cupons";
import { OfferPaymentMethod, OfferSimulation, simulateOffer } from "../../../lib/ofertas";

type FormState = {
  code: string;
  discountType: CouponKind;
  maxPerBuyer: number;
  offerIds: string[];
};

const blankForm: FormState = { code: "", discountType: "PERCENT", maxPerBuyer: 1, offerIds: [] };

function inputFromCoupon(coupon: Coupon): FormState {
  return { code: coupon.code, discountType: coupon.discountType, maxPerBuyer: coupon.maxPerBuyer, offerIds: coupon.offerIds };
}

function dateLabel(value: string) {
  return new Intl.DateTimeFormat("pt-BR", { dateStyle: "long", timeStyle: "short" }).format(new Date(value));
}

export function CouponForm({ couponId }: { couponId?: string }) {
  const [coupon, setCoupon] = useState<Coupon | null>(null);
  const [values, setValues] = useState<FormState>(blankForm);
  const [valueText, setValueText] = useState("");
  const [startsAtText, setStartsAtText] = useState("");
  const [expiresAtText, setExpiresAtText] = useState("");
  const [maxRedemptionsText, setMaxRedemptionsText] = useState("");
  const [errors, setErrors] = useState<CouponInputErrors>({});
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [loading, setLoading] = useState(Boolean(couponId));
  const [notFound, setNotFound] = useState(false);
  const [loadFailed, setLoadFailed] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);

  const [offerOptions, setOfferOptions] = useState<OfferOption[] | null>(null);
  const [offersFailed, setOffersFailed] = useState(false);

  const [simulationOfferId, setSimulationOfferId] = useState("");
  const [simulationMethod, setSimulationMethod] = useState<OfferPaymentMethod>("PIX");
  const [simulationInstallments, setSimulationInstallments] = useState(1);
  const [simulationLoading, setSimulationLoading] = useState(false);
  const [simulationError, setSimulationError] = useState<string | null>(null);
  const [simulation, setSimulation] = useState<OfferSimulation | null>(null);

  useEffect(() => {
    let active = true;
    listOfferOptions().then(options => { if (active) setOfferOptions(options); }).catch(() => { if (active) setOffersFailed(true); });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (!couponId) return;
    let active = true;
    getCoupon(couponId).then(loaded => {
      if (!active) return;
      setCoupon(loaded);
      setValues(inputFromCoupon(loaded));
      setValueText(loaded.discountType === "PERCENT" ? formatBps(loaded.discountBps ?? 0).replace("%", "") : formatFixedDiscount(loaded.discountCents ?? 0));
      setStartsAtText(toDatetimeLocal(loaded.startsAt));
      setExpiresAtText(toDatetimeLocal(loaded.expiresAt));
      setMaxRedemptionsText(loaded.maxRedemptions === null ? "" : String(loaded.maxRedemptions));
      setSimulationOfferId(loaded.offerIds[0] ?? "");
    }).catch(error => {
      if (!active) return;
      if (error instanceof ApiRequestError && error.status === 404) setNotFound(true);
      else setLoadFailed(true);
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [couponId]);

  function change<K extends keyof FormState>(field: K, value: FormState[K]) {
    setValues(current => ({ ...current, [field]: value }));
    setErrors(current => ({ ...current, [field]: undefined }));
    setGeneralError(null);
    setSaved(false);
  }

  function toggleOffer(offerId: string, checked: boolean) {
    const offerIds = checked ? [...new Set([...values.offerIds, offerId])] : values.offerIds.filter(id => id !== offerId);
    change("offerIds", offerIds);
  }

  function payload(): CouponInput | null {
    const startsAt = fromDatetimeLocal(startsAtText);
    const expiresAt = fromDatetimeLocal(expiresAtText);
    const maxRedemptions = maxRedemptionsText.trim() === "" ? null : Number(maxRedemptionsText);
    const discountBps = values.discountType === "PERCENT" ? parsePercentToBps(valueText) : null;
    const discountCents = values.discountType === "FIXED" ? parseFixedDiscount(valueText) : null;

    const nextErrors: CouponInputErrors = {};
    if (startsAtText.trim() && startsAt === null) nextErrors.startsAt = "Data de início inválida.";
    if (expiresAtText.trim() && expiresAt === null) nextErrors.expiresAt = "Data de vencimento inválida.";
    if (maxRedemptionsText.trim() !== "" && !Number.isInteger(maxRedemptions)) nextErrors.maxRedemptions = "Use um número inteiro.";

    const next: CouponInput = {
      code: values.code.trim().toUpperCase(),
      discountType: values.discountType,
      discountBps,
      discountCents,
      startsAt,
      expiresAt,
      maxRedemptions,
      maxPerBuyer: values.maxPerBuyer,
      offerIds: values.offerIds,
    };

    const validation = { ...validateCouponInput(next), ...nextErrors };
    if (Object.keys(validation).length) {
      setErrors(validation);
      setGeneralError("Revise os campos destacados.");
      return null;
    }
    return next;
  }

  async function save(event?: FormEvent) {
    event?.preventDefault();
    const next = payload();
    if (!next) return;
    setSaving(true);
    setGeneralError(null);
    try {
      const stored = coupon ? await updateCoupon(coupon.id, next) : await createCoupon(next);
      setCoupon(stored);
      setValues(inputFromCoupon(stored));
      setSimulationOfferId(current => current || stored.offerIds[0] || "");
      setSaved(true);
    } catch (error) {
      if (error instanceof ApiRequestError) {
        setErrors(fieldErrors(error.problem) as CouponInputErrors);
        setGeneralError(error.message);
      } else setGeneralError("Não foi possível salvar o cupom. Tente novamente.");
    } finally {
      setSaving(false);
    }
  }

  async function simulate() {
    if (!coupon || !simulationOfferId) return;
    setSimulationLoading(true);
    setSimulationError(null);
    try {
      setSimulation(await simulateOffer(simulationOfferId, simulationMethod, simulationInstallments, coupon.code));
    } catch (error) {
      setSimulation(null);
      setSimulationError(error instanceof ApiRequestError ? error.message : "Não foi possível consultar a simulação.");
    } finally {
      setSimulationLoading(false);
    }
  }

  const offerLabel = useMemo(() => (id: string) => offerOptions?.find(option => option.id === id)?.label ?? id, [offerOptions]);

  if (loading) return <Skeleton label="Carregando formulário do cupom" />;
  if (notFound) return <EmptyState title="Cupom não encontrado" description="O cupom não existe, foi arquivado ou não está disponível para esta conta." action={<Link className="ui-button ui-button-secondary" href="/cupons">Voltar aos cupons</Link>} />;
  if (loadFailed) return <Toast tone="danger">Não foi possível carregar os dados do cupom. <Link href="/cupons">Voltar aos cupons</Link></Toast>;

  const valueLabel = values.discountType === "PERCENT" ? "Desconto (%)" : "Desconto (R$)";
  const valuePlaceholder = values.discountType === "PERCENT" ? "Ex.: 10" : "Ex.: 20,00";

  return <>
    <nav className="breadcrumb" aria-label="Navegação estrutural"><Link href="/cupons">Cupons</Link><span aria-hidden="true">/</span><span aria-current="page">{couponId ? "Editar cupom" : "Novo cupom"}</span></nav>
    <header className="content-header"><div><h1>{couponId ? "Editar cupom" : "Novo cupom"}</h1><p>Configure a validade e o limite do cupom sem calcular descontos no navegador.</p></div></header>
    <form className="ui-card offer-form" onSubmit={event => void save(event)} noValidate>
      {generalError && <Toast tone="danger">{generalError}</Toast>}
      {saved && !generalError && <Toast tone="success">Cupom salvo.</Toast>}

      <section aria-labelledby="coupon-basics-title"><h2 id="coupon-basics-title">Dados do cupom</h2>
        <div className="offer-form-grid">
          <Campo label="Código" value={values.code} maxLength={32} error={errors.code} hint="3 a 32 letras, números, '-' ou '_'." onChange={event => change("code", event.target.value.toUpperCase())} required />
          <Select label="Tipo de desconto" value={values.discountType} onChange={event => { change("discountType", event.target.value as CouponKind); setValueText(""); }}>
            <option value="PERCENT">Percentual</option>
            <option value="FIXED">Valor fixo</option>
          </Select>
          <Campo label={valueLabel} value={valueText} inputMode="decimal" placeholder={valuePlaceholder} error={errors.value} onChange={event => { setValueText(event.target.value); setErrors(current => ({ ...current, value: undefined })); setSaved(false); }} required />
          <Campo label="Limite por comprador" type="number" min={1} value={values.maxPerBuyer} error={errors.maxPerBuyer} onChange={event => change("maxPerBuyer", Number(event.target.value))} required />
          <Campo label="Limite total de resgates" type="number" min={1} value={maxRedemptionsText} hint="Deixe em branco para não limitar." error={errors.maxRedemptions} onChange={event => { setMaxRedemptionsText(event.target.value); setErrors(current => ({ ...current, maxRedemptions: undefined })); setSaved(false); }} />
          <Campo label="Início da validade" type="datetime-local" value={startsAtText} hint="Deixe em branco para valer imediatamente." error={errors.startsAt} onChange={event => { setStartsAtText(event.target.value); setErrors(current => ({ ...current, startsAt: undefined })); setSaved(false); }} />
          <Campo label="Vencimento" type="datetime-local" value={expiresAtText} hint="Deixe em branco para não expirar." error={errors.expiresAt} onChange={event => { setExpiresAtText(event.target.value); setErrors(current => ({ ...current, expiresAt: undefined })); setSaved(false); }} />
        </div>
      </section>

      {coupon && <section aria-labelledby="coupon-usage-title"><h2 id="coupon-usage-title">Consumo</h2>
        <dl className="simulation-result">
          <div><dt>Resgatado</dt><dd className="paysi-valor">{coupon.maxRedemptions === null ? `${coupon.redeemedCount} (sem limite)` : `${coupon.redeemedCount} de ${coupon.maxRedemptions}`}</dd></div>
          <div><dt>Criado em</dt><dd>{dateLabel(coupon.createdAt)}</dd></div>
        </dl>
      </section>}

      <section aria-labelledby="coupon-offers-title"><h2 id="coupon-offers-title">Ofertas participantes</h2>
        {offersFailed ? <Toast tone="danger">Não foi possível carregar as ofertas.</Toast> :
          !offerOptions ? <Skeleton label="Carregando ofertas" /> :
          offerOptions.length === 0 ? <EmptyState headingLevel="h3" title="Nenhuma oferta disponível" description="Crie uma oferta antes de cadastrar um cupom." /> :
          <fieldset className="offer-choice-group"><legend>Selecione as ofertas em que o cupom vale</legend>
            {offerOptions.map(option => <Checkbox key={option.id} label={option.label} checked={values.offerIds.includes(option.id)} onChange={event => toggleOffer(option.id, event.target.checked)} />)}
            {errors.offerIds && <small className="ui-error">{errors.offerIds}</small>}
          </fieldset>}
      </section>

      <section className="offer-simulation" aria-labelledby="coupon-simulation-title">
        <div className="section-heading"><div><h2 id="coupon-simulation-title">Prévia</h2><p>Os valores abaixo vêm do backend e usam o mesmo cálculo do checkout.</p></div>
          <Botao type="button" variant="secondary" disabled={simulationLoading || !coupon || !simulationOfferId} onClick={() => void simulate()}>{simulationLoading ? "Consultando…" : "Consultar prévia"}</Botao>
        </div>
        {!coupon && <p className="ui-hint">Salve o cupom para consultar a prévia.</p>}
        {coupon && <div className="offer-form-grid">
          <Select label="Oferta" value={simulationOfferId} onChange={event => setSimulationOfferId(event.target.value)}>
            {values.offerIds.map(id => <option key={id} value={id}>{offerLabel(id)}</option>)}
          </Select>
          <Select label="Método" value={simulationMethod} onChange={event => setSimulationMethod(event.target.value as OfferPaymentMethod)}>
            <option value="PIX">Pix</option><option value="CARD">Cartão</option><option value="BOLETO">Boleto</option>
          </Select>
          <Campo label="Parcelas" type="number" min={1} max={12} disabled={simulationMethod !== "CARD"} value={simulationInstallments} onChange={event => setSimulationInstallments(Number(event.target.value))} />
        </div>}
        {simulationError && <Toast tone="danger">{simulationError}</Toast>}
        {simulation && <dl className="simulation-result">
          <div><dt>Preço</dt><dd>{formatFixedDiscount(simulation.grossCents)}</dd></div>
          <div><dt>Desconto do cupom</dt><dd>{formatFixedDiscount(simulation.discountCents)}</dd></div>
          <div><dt>Pago</dt><dd>{formatFixedDiscount(simulation.paidCents)}</dd></div>
        </dl>}
      </section>

      <div className="ui-actions product-form-actions">
        <Botao type="submit" disabled={saving}>{saving ? "Salvando…" : "Salvar cupom"}</Botao>
        <Link className="ui-button ui-button-secondary" href="/cupons">Cancelar</Link>
      </div>
    </form>
  </>;
}
