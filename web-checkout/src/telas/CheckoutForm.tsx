import { FormEvent, useRef, useState } from "react";
import { CheckoutContract, PaymentMethod, PersonType } from "../lib/checkout";
import { ApiRequestError } from "../lib/api";
import { ChaveCampo, comoChaveCampo, validarCampo } from "../lib/camposComprador";
import { CamposComprador } from "../componentes/CamposComprador";
import { Metodo, SeletorDeMetodo } from "../componentes/SeletorDeMetodo";
import { criarPedido } from "../lib/pedido";
import { calcularTermosHash } from "../lib/termos";
import { obterChaveDeIdempotencia } from "../lib/idempotencia";
import { formatarCentavos } from "../lib/formato";
import { SimulacaoCheckout, simularCheckout } from "../lib/simulacao";

const METHOD_TO_METODO: Record<PaymentMethod, Metodo> = { CARD: "cartao", PIX: "pix", BOLETO: "boleto" };
const METODO_TO_METHOD: Record<Metodo, PaymentMethod> = { cartao: "CARD", pix: "PIX", boleto: "BOLETO" };

export function CheckoutForm({ slug, contract }: { slug: string; contract: CheckoutContract }) {
  const [personType, setPersonType] = useState<PersonType>("PF");
  const [values, setValues] = useState<Record<string, string>>({});
  const [errors, setErrors] = useState<Record<string, string | undefined>>({});
  const [couponVisible, setCouponVisible] = useState(false);
  const [coupon, setCoupon] = useState("");
  const [couponError, setCouponError] = useState<string | null>(null);
  const [simulando, setSimulando] = useState(false);
  const [simulacao, setSimulacao] = useState<SimulacaoCheckout | null>(null);
  const [termsAccepted, setTermsAccepted] = useState(false);
  const [termsError, setTermsError] = useState<string | null>(null);
  const [metodo, setMetodo] = useState<Metodo>(METHOD_TO_METODO[contract.methods[0] ?? "PIX"]);
  const [installments, setInstallments] = useState(1);
  const [submitting, setSubmitting] = useState(false);
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [submitted, setSubmitted] = useState(false);
  const simulationVersion = useRef(0);

  const disponiveis = contract.methods.map(method => METHOD_TO_METODO[method]);
  const campos = (contract.requiredBuyerFields[personType] ?? [])
    .map(comoChaveCampo)
    .filter((key): key is ChaveCampo => key !== null);
  const paymentMethod = METODO_TO_METHOD[metodo];
  const selectedInstallments = metodo === "cartao" ? installments : 1;

  function invalidarSimulacao() {
    simulationVersion.current += 1;
    setSimulando(false);
    setSimulacao(null);
    setCouponError(null);
    setGeneralError(null);
  }

  async function aplicarCupom() {
    const couponCode = coupon.trim();
    if (!couponCode) {
      setCouponError("Informe o código do cupom.");
      return;
    }
    setSimulando(true);
    setCouponError(null);
    setSimulacao(null);
    const requestVersion = ++simulationVersion.current;
    try {
      const result = await simularCheckout(slug, {
        method: paymentMethod,
        installments: selectedInstallments,
        couponCode,
      });
      if (simulationVersion.current === requestVersion) setSimulacao(result);
    } catch (error) {
      if (simulationVersion.current === requestVersion) {
        setCouponError(error instanceof ApiRequestError ? error.message : "Não foi possível validar o cupom.");
      }
    } finally {
      if (simulationVersion.current === requestVersion) setSimulando(false);
    }
  }

  function change(key: ChaveCampo, value: string) {
    setValues(current => ({ ...current, [key]: value }));
    setErrors(current => ({ ...current, [key]: undefined }));
    setGeneralError(null);
  }

  function changePersonType(next: PersonType) {
    setPersonType(next);
    setErrors({});
    setGeneralError(null);
  }

  async function enviar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextErrors: Record<string, string | undefined> = {};
    for (const key of campos) {
      const error = validarCampo(key, values[key] ?? "", personType);
      if (error) nextErrors[key] = error;
    }
    const hasTermsError = !termsAccepted;
    const hasCouponError = couponVisible && Boolean(coupon.trim()) && !simulacao;
    setErrors(nextErrors);
    setTermsError(hasTermsError ? "É preciso aceitar os termos para continuar." : null);
    setCouponError(hasCouponError ? "Valide o cupom antes de continuar." : null);
    if (Object.values(nextErrors).some(Boolean) || hasTermsError || hasCouponError) {
      setGeneralError("Revise os campos destacados.");
      return;
    }

    setSubmitting(true);
    setGeneralError(null);
    try {
      const termsHash = await calcularTermosHash(contract.legalTexts.termsUrl);
      await criarPedido(slug, {
        buyer: montarComprador(values, personType, campos),
        method: paymentMethod,
        installments: selectedInstallments,
        coupon: couponVisible && coupon.trim() ? coupon.trim() : null,
        termsHash,
      }, obterChaveDeIdempotencia());
      setSubmitted(true);
    } catch (error) {
      setGeneralError(error instanceof ApiRequestError ? error.message : "Não foi possível concluir a compra. Tente novamente.");
    } finally {
      setSubmitting(false);
    }
  }

  if (submitted) {
    return (
      <div className="success-panel" role="status">
        <h2>Pedido recebido</h2>
        <p>Você receberá a confirmação por e-mail assim que o pagamento for processado.</p>
      </div>
    );
  }

  return (
    <form className="checkout-form" onSubmit={event => void enviar(event)} noValidate>
      {generalError && <p className="form-alert" role="alert">{generalError}</p>}

      <div><span className="step">1</span><h2>Quem está comprando</h2></div>
      <fieldset className="person-type-toggle">
        <legend>Tipo de comprador</legend>
        <label className={personType === "PF" ? "selected" : undefined}>
          <input type="radio" name="personType" checked={personType === "PF"} onChange={() => changePersonType("PF")} /> Pessoa física
        </label>
        <label className={personType === "PJ" ? "selected" : undefined}>
          <input type="radio" name="personType" checked={personType === "PJ"} onChange={() => changePersonType("PJ")} /> Pessoa jurídica
        </label>
      </fieldset>
      <CamposComprador campos={campos} values={values} errors={errors} personType={personType} onChange={change} />

      <div className="section-title"><span className="step">2</span><h2>Cupom de desconto</h2></div>
      {!couponVisible ? (
        <button type="button" className="link-button" onClick={() => setCouponVisible(true)}>Tenho um cupom</button>
      ) : (
        <label htmlFor="coupon">Código do cupom
          <span className="coupon-row">
            <input id="coupon" value={coupon} autoComplete="off" aria-invalid={Boolean(couponError)}
              aria-describedby={couponError ? "coupon-error" : "coupon-hint"}
              onChange={event => { setCoupon(event.target.value.toUpperCase()); invalidarSimulacao(); }} />
            <button type="button" className="secondary-button" disabled={simulando} onClick={() => void aplicarCupom()}>
              {simulando ? "Validando…" : "Aplicar"}
            </button>
          </span>
          {couponError
            ? <small id="coupon-error" className="field-error" role="alert">{couponError}</small>
            : <small id="coupon-hint" className="field-hint">O desconto é calculado exclusivamente pela Paysi.</small>}
          {simulacao && (
            <span className="coupon-result" role="status">
              <span>Desconto <strong>− {formatarCentavos(simulacao.discountCents)}</strong></span>
              <span>Total após cupom <strong>{formatarCentavos(simulacao.paidCents)}</strong></span>
            </span>
          )}
        </label>
      )}

      <div className="section-title"><span className="step">3</span><h2>Pagamento</h2></div>
      <SeletorDeMetodo value={metodo} onChange={next => { setMetodo(next); invalidarSimulacao(); }} disponiveis={disponiveis} />
      {metodo === "cartao" && (
        <>
          {contract.installments > 1 && (
            <label className="installments-field">Parcelas
              <select value={installments} onChange={event => { setInstallments(Number(event.target.value)); invalidarSimulacao(); }}>
                {Array.from({ length: contract.installments }, (_, index) => index + 1).map(n => (
                  <option key={n} value={n}>{n}x</option>
                ))}
              </select>
            </label>
          )}
          <div className="provider-frame" role="group" aria-label="Dados do cartão">
            <p>O campo seguro do provedor de pagamento será carregado aqui.</p>
            <small>A Paysi nunca recebe os dados do seu cartão.</small>
          </div>
        </>
      )}

      <div className="section-title"><span className="step">4</span><h2>Termos</h2></div>
      <label className="terms-check">
        <input type="checkbox" checked={termsAccepted} onChange={event => { setTermsAccepted(event.target.checked); setTermsError(null); }} />
        Li e aceito os <a href={contract.legalTexts.termsUrl} target="_blank" rel="noreferrer">termos de uso</a> e a{" "}
        <a href={contract.legalTexts.privacyUrl} target="_blank" rel="noreferrer">política de privacidade</a>.
      </label>
      {termsError && <small className="field-error">{termsError}</small>}

      <button className="pay-button" type="submit" disabled={submitting} style={{ background: contract.appearance.primaryColor }}>
        {submitting ? "Processando…" : contract.appearance.buttonText}
      </button>
      <p className="security-note">Compra protegida e processada em ambiente seguro.</p>
    </form>
  );
}

function montarComprador(values: Record<string, string>, personType: PersonType, campos: ChaveCampo[]) {
  const has = (key: ChaveCampo) => campos.includes(key);
  return {
    name: values.name ?? "",
    email: values.email ?? "",
    personType,
    taxId: (values.taxId ?? "").replace(/\D/g, ""),
    ...(has("legalName") ? { legalName: values.legalName } : {}),
    ...(has("municipalReg") ? { municipalReg: values.municipalReg } : {}),
    ...(has("address.zipCode") ? {
      address: {
        zipCode: (values["address.zipCode"] ?? "").replace(/\D/g, ""),
        street: values["address.street"] ?? "",
        number: values["address.number"] ?? "",
        complement: values["address.complement"] ?? "",
        district: values["address.district"] ?? "",
        city: values["address.city"] ?? "",
        state: values["address.state"] ?? "",
      },
    } : {}),
  };
}
