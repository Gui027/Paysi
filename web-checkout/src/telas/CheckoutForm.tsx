import { FormEvent, useState } from "react";
import { CheckoutContract, PaymentMethod, PersonType } from "../lib/checkout";
import { ApiRequestError } from "../lib/api";
import { ChaveCampo, comoChaveCampo, validarCampo } from "../lib/camposComprador";
import { CamposComprador } from "../componentes/CamposComprador";
import { Metodo, SeletorDeMetodo } from "../componentes/SeletorDeMetodo";
import { criarPedido } from "../lib/pedido";
import { calcularTermosHash } from "../lib/termos";
import { obterChaveDeIdempotencia } from "../lib/idempotencia";

const METHOD_TO_METODO: Record<PaymentMethod, Metodo> = { CARD: "cartao", PIX: "pix", BOLETO: "boleto" };
const METODO_TO_METHOD: Record<Metodo, PaymentMethod> = { cartao: "CARD", pix: "PIX", boleto: "BOLETO" };

export function CheckoutForm({ slug, contract }: { slug: string; contract: CheckoutContract }) {
  const [personType, setPersonType] = useState<PersonType>("PF");
  const [values, setValues] = useState<Record<string, string>>({});
  const [errors, setErrors] = useState<Record<string, string | undefined>>({});
  const [couponVisible, setCouponVisible] = useState(false);
  const [coupon, setCoupon] = useState("");
  const [termsAccepted, setTermsAccepted] = useState(false);
  const [termsError, setTermsError] = useState<string | null>(null);
  const [metodo, setMetodo] = useState<Metodo>(METHOD_TO_METODO[contract.methods[0] ?? "PIX"]);
  const [installments, setInstallments] = useState(1);
  const [submitting, setSubmitting] = useState(false);
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [submitted, setSubmitted] = useState(false);

  const disponiveis = contract.methods.map(method => METHOD_TO_METODO[method]);
  const campos = (contract.requiredBuyerFields[personType] ?? [])
    .map(comoChaveCampo)
    .filter((key): key is ChaveCampo => key !== null);

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
    setErrors(nextErrors);
    setTermsError(hasTermsError ? "É preciso aceitar os termos para continuar." : null);
    if (Object.values(nextErrors).some(Boolean) || hasTermsError) {
      setGeneralError("Revise os campos destacados.");
      return;
    }

    setSubmitting(true);
    setGeneralError(null);
    try {
      const termsHash = await calcularTermosHash(contract.legalTexts.termsUrl);
      await criarPedido(slug, {
        buyer: montarComprador(values, personType, campos),
        method: METODO_TO_METHOD[metodo],
        installments: metodo === "cartao" ? installments : 1,
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
          <input id="coupon" value={coupon} autoComplete="off" onChange={event => setCoupon(event.target.value.toUpperCase())} />
          <small className="field-hint">O desconto, se houver, é calculado pelo servidor ao confirmar a compra.</small>
        </label>
      )}

      <div className="section-title"><span className="step">3</span><h2>Pagamento</h2></div>
      <SeletorDeMetodo value={metodo} onChange={setMetodo} disponiveis={disponiveis} />
      {metodo === "cartao" && (
        <>
          {contract.installments > 1 && (
            <label className="installments-field">Parcelas
              <select value={installments} onChange={event => setInstallments(Number(event.target.value))}>
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
