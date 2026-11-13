import { FormEvent, useEffect, useRef, useState } from "react";
import { CheckoutContract, PaymentMethod, PersonType } from "../lib/checkout";
import { ApiRequestError } from "../lib/api";
import { ChaveCampo, validarCampo } from "../lib/camposComprador";
import { Campo } from "../componentes/Campo";
import { CamposComprador } from "../componentes/CamposComprador";
import { CampoDeCartao } from "../componentes/CampoDeCartao";
import { DesafioTresDS } from "../componentes/DesafioTresDS";
import { Metodo, SeletorDeMetodo } from "../componentes/SeletorDeMetodo";
import { criarPedido } from "../lib/pedido";
import { calcularTermosHash } from "../lib/termos";
import { obterChaveDeIdempotencia } from "../lib/idempotencia";
import { formatarCentavos, lerCentavos } from "../lib/formato";
import { SimulacaoCheckout, simularCheckout } from "../lib/simulacao";
import { CobrancaIniciada, cobrancaAprovada, cobrancaRecusada, confirmarTresDs, exigeDesafioTresDs, iniciarCobranca, obterChaveDeDispositivo, tokenizarCartao } from "../lib/cobranca";
import { DadosCartao } from "../lib/cartao";
import { Aprovado } from "./Aprovado";
import { Recusado } from "./Recusado";
import { PixAguardando } from "./PixAguardando";
import { BoletoEmitido } from "./BoletoEmitido";
import { lerParametrosIntegracao, montarUrlRetorno } from "../lib/integracao";
import { inferirTipoPessoa } from "../lib/documento";
import { formatarTelefone } from "../lib/mascaras";
import { consultarCep } from "../lib/cep";

const METHOD_TO_METODO: Record<PaymentMethod, Metodo> = { CARD: "cartao", PIX: "pix", BOLETO: "boleto" };
const METODO_TO_METHOD: Record<Metodo, PaymentMethod> = { cartao: "CARD", pix: "PIX", boleto: "BOLETO" };

export function CheckoutForm({ slug, contract, preview = false }: { slug: string; contract: CheckoutContract; preview?: boolean }) {
  // O link do vendedor pode trazer ?ref=, ?email= e ?name= (integração com o sistema dele).
  const [integracao] = useState(() => lerParametrosIntegracao(window.location.search));
  const [values, setValues] = useState<Record<string, string>>(() => ({
    ...(integracao.name ? { name: integracao.name } : {}),
    ...(integracao.email ? { email: integracao.email } : {}),
  }));
  const urlRetorno = montarUrlRetorno(contract.returnUrl, integracao.reference);
  const [errors, setErrors] = useState<Record<string, string | undefined>>({});
  const [couponVisible, setCouponVisible] = useState(false);
  const [coupon, setCoupon] = useState("");
  const [amountText, setAmountText] = useState("");
  const [amountError, setAmountError] = useState<string | null>(null);
  const [couponError, setCouponError] = useState<string | null>(null);
  const [simulando, setSimulando] = useState(false);
  const [simulacao, setSimulacao] = useState<SimulacaoCheckout | null>(null);
  const [metodo, setMetodo] = useState<Metodo>(METHOD_TO_METODO[contract.methods.includes("CARD") ? "CARD" : (contract.methods[0] ?? "PIX")]);
  const [installments, setInstallments] = useState(1);
  const [submitting, setSubmitting] = useState(false);
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [submitted, setSubmitted] = useState(false);
  const [dadosCartao, setDadosCartao] = useState<DadosCartao | null>(null);
  const [cobranca, setCobranca] = useState<CobrancaIniciada | null>(null);
  const [confirmandoTresDs, setConfirmandoTresDs] = useState(false);
  const [consultandoCep, setConsultandoCep] = useState(false);
  const [cepResolvido, setCepResolvido] = useState<string | null>(null);
  const termsHashRef = useRef<string | null>(null);
  const simulationVersion = useRef(0);

  const disponiveis = (["CARD", "PIX", "BOLETO"] as PaymentMethod[])
    .filter(method => contract.methods.includes(method))
    .map(method => METHOD_TO_METODO[method]);
  const personType: PersonType = inferirTipoPessoa(values.taxId ?? "");
  const exigeEndereco = contract.segment === "PHYSICAL" || metodo === "cartao";
  const camposEndereco: ChaveCampo[] = exigeEndereco ? ["address.zipCode", "address.number"] : [];
  const camposOcultosEndereco: ChaveCampo[] = contract.segment === "PHYSICAL"
    ? ["address.street", "address.district", "address.city", "address.state"] : [];
  const campos: ChaveCampo[] = ["name", "email", "taxId", ...camposEndereco, ...camposOcultosEndereco];
  const paymentMethod = METODO_TO_METHOD[metodo];
  const selectedInstallments = metodo === "cartao" ? installments : 1;
  const customerDefinesAmount = contract.pricingMode === "CUSTOMER_DEFINED";

  useEffect(() => {
    if (!exigeEndereco) return;
    const cep = (values["address.zipCode"] ?? "").replace(/\D/g, "");
    if (cep.length !== 8) {
      setCepResolvido(null);
      setConsultandoCep(false);
      return;
    }
    if (cepResolvido === cep) return;
    const controller = new AbortController();
    setConsultandoCep(true);
    consultarCep(cep, controller.signal).then(endereco => {
      setValues(current => ({
        ...current,
        "address.street": endereco.street,
        "address.district": endereco.district,
        "address.city": endereco.city,
        "address.state": endereco.state,
      }));
      setErrors(current => ({ ...current, "address.zipCode": undefined }));
      setCepResolvido(cep);
    }).catch(error => {
      if (error instanceof DOMException && error.name === "AbortError") return;
      setCepResolvido(null);
      setErrors(current => ({ ...current, "address.zipCode": "CEP não encontrado." }));
    }).finally(() => {
      if (!controller.signal.aborted) setConsultandoCep(false);
    });
    return () => controller.abort();
  }, [values["address.zipCode"], exigeEndereco, cepResolvido]);

  function selectedAmount(): number | null {
    if (!customerDefinesAmount) return null;
    const cents = lerCentavos(amountText);
    if (cents === null) {
      setAmountError("Informe o valor que deseja pagar.");
      return null;
    }
    if (cents < contract.priceCents) {
      setAmountError(`O valor mínimo é ${formatarCentavos(contract.priceCents)}.`);
      return null;
    }
    setAmountError(null);
    return cents;
  }

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
    const amountCents = selectedAmount();
    if (customerDefinesAmount && amountCents === null) {
      setSimulando(false);
      return;
    }
    try {
      const result = await simularCheckout(slug, {
        method: paymentMethod,
        installments: selectedInstallments,
        couponCode,
        ...(amountCents !== null ? { amountCents } : {}),
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

  async function enviar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (preview) return;
    const nextErrors: Record<string, string | undefined> = {};
    for (const key of campos) {
      const error = validarCampo(key, values[key] ?? "", personType);
      if (error) nextErrors[key] = error;
    }
    // Cadastro mínimo do checkout: o telefone é obrigatório e também é reutilizado
    // na tokenização do cartão, sem pedir o mesmo dado duas vezes.
    const telefone = (values.phone ?? "").replace(/\D/g, "");
    if (telefone.length < 10 || telefone.length > 11) nextErrors.phone = "Informe o telefone com DDD.";
    const hasCouponError = couponVisible && Boolean(coupon.trim()) && !simulacao;
    const hasCardError = metodo === "cartao" && !dadosCartao;
    const hasCepError = exigeEndereco && (consultandoCep || !cepResolvido);
    const amountCents = selectedAmount();
    const hasAmountError = customerDefinesAmount && amountCents === null;
    setCouponError(hasCouponError ? "Valide o cupom antes de continuar." : null);
    if (hasCepError) nextErrors["address.zipCode"] = consultandoCep ? "Aguarde a consulta do CEP." : "Informe um CEP válido.";
    setErrors(nextErrors);
    if (Object.values(nextErrors).some(Boolean) || hasCouponError || hasCardError || hasAmountError || hasCepError) {
      setGeneralError(hasCardError ? "Preencha os dados do cartão corretamente." : "Revise os campos destacados.");
      return;
    }

    setSubmitting(true);
    setGeneralError(null);
    try {
      const termsHash = await calcularTermosHash(contract.legalTexts.termsUrl);
      termsHashRef.current = termsHash;
      const payload = {
        buyer: { ...montarComprador(values, personType, campos), ...(telefone ? { phone: telefone } : {}) },
        method: paymentMethod,
        installments: selectedInstallments,
        coupon: couponVisible && coupon.trim() ? coupon.trim() : null,
        termsHash,
        ...(integracao.reference ? { reference: integracao.reference } : {}),
        ...(amountCents !== null ? { amountCents } : {}),
      };
      const pedido = await criarPedido(slug, payload, obterChaveDeIdempotencia(`${slug}:${JSON.stringify(payload)}`));

      // O cartão só sai do navegador agora, com o pedido já criado: o backend troca por um token
      // do provedor (vinculado ao comprador do pedido) e a cobrança usa só o token.
      const cardToken = metodo === "cartao" && dadosCartao
        ? (await tokenizarCartao(pedido.orderId, dadosCartao)).cardToken
        : null;
      const resultado = await iniciarCobranca(pedido.orderId, {
        cardToken,
        deviceKey: obterChaveDeDispositivo(),
        termsHash,
        termsAcceptedAt: new Date().toISOString(),
      });
      setCobranca(resultado);
      setSubmitted(true);
    } catch (error) {
      setGeneralError(error instanceof ApiRequestError ? error.message : "Não foi possível concluir a compra. Tente novamente.");
    } finally {
      setSubmitting(false);
    }
  }

  async function confirmarDesafio(challengeToken: string) {
    if (!cobranca || !termsHashRef.current) return;
    setConfirmandoTresDs(true);
    try {
      const resultado = await confirmarTresDs(cobranca.chargeId, {
        challengeToken,
        deviceKey: obterChaveDeDispositivo(),
        termsHash: termsHashRef.current,
        termsAcceptedAt: new Date().toISOString(),
      });
      setCobranca(resultado);
    } catch (error) {
      setGeneralError(error instanceof ApiRequestError ? error.message : "Não foi possível confirmar o desafio 3DS.");
    } finally {
      setConfirmandoTresDs(false);
    }
  }

  function tentarNovamenteComPix() {
    setSubmitted(false);
    setCobranca(null);
    setDadosCartao(null);
    setMetodo("pix");
    setGeneralError(null);
  }

  if (submitted && cobranca) {
    if (cobranca.method === "CARD") {
      if (exigeDesafioTresDs(cobranca) && cobranca.threeDs?.challengeUrl) {
        return (
          <DesafioTresDS
            challengeUrl={cobranca.threeDs.challengeUrl}
            confirmando={confirmandoTresDs}
            onConfirmar={challengeToken => void confirmarDesafio(challengeToken)}
          />
        );
      }
      if (cobrancaAprovada(cobranca)) return <Aprovado urlRetorno={urlRetorno} />;
      if (cobrancaRecusada(cobranca)) {
        return <Recusado onTentarPix={disponiveis.includes("pix") ? tentarNovamenteComPix : undefined} />;
      }
    }
    if (cobranca.method === "PIX" && cobranca.pixQrCode) {
      return <PixAguardando chargeId={cobranca.chargeId} qrCode={cobranca.pixQrCode} expiresAt={cobranca.expiresAt} urlRetorno={urlRetorno} />;
    }
    if (cobranca.method === "BOLETO" && cobranca.boletoBarcode && cobranca.boletoUrl) {
      return (
        <BoletoEmitido
          chargeId={cobranca.chargeId}
          barcode={cobranca.boletoBarcode}
          boletoUrl={cobranca.boletoUrl}
          dueAt={cobranca.expiresAt}
        />
      );
    }
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

      {customerDefinesAmount && <><h2 className="section-heading"><span className="step">1</span>Escolha o valor</h2><div className="field-grid customer-amount"><Campo id="amount" label={`Valor a pagar (mínimo ${formatarCentavos(contract.priceCents)})`} inputMode="decimal" placeholder="0,00" full value={amountText} error={amountError ?? undefined} onChange={event => { setAmountText(event.target.value); setAmountError(null); invalidarSimulacao(); }} /></div></>}

      <h2 className="section-heading"><span className="step">{customerDefinesAmount ? 2 : 1}</span>Seus dados</h2>
      <div className="buyer-fields-layout">
        <CamposComprador campos={campos.filter(key => !key.startsWith("address."))}
          values={values} errors={errors} personType={personType} onChange={change} />
        <div className="field-grid buyer-phone">
          <Campo id="phone" label="Telefone" hideLabel type="tel" inputMode="tel" autoComplete="tel"
            placeholder="Telefone com DDD" value={values.phone ?? ""} error={errors.phone} maxLength={15}
            onChange={event => { setValues(current => ({ ...current, phone: formatarTelefone(event.target.value) })); setErrors(current => ({ ...current, phone: undefined })); setGeneralError(null); }} />
        </div>
      </div>

      <h2 className="section-heading"><span className="step">{customerDefinesAmount ? 3 : 2}</span>Forma de pagamento</h2>
      <SeletorDeMetodo value={metodo} onChange={next => { setMetodo(next); invalidarSimulacao(); setDadosCartao(null); }} disponiveis={disponiveis} />
      {metodo === "cartao" && contract.segment !== "PHYSICAL" && (
        <div className="payment-address-fields">
          <CamposComprador campos={["address.zipCode", "address.number"]}
            values={values} errors={errors} personType={personType} onChange={change} />
          <small className="field-hint" role="status">
            {consultandoCep ? "Buscando endereço…" : cepResolvido ? "Endereço localizado." : "Informe o CEP para localizar o endereço."}
          </small>
        </div>
      )}
      {metodo === "cartao" && (
        <div className="card-payment-panel">
          <CampoDeCartao nomeTitular={values.name ?? ""} telefone={values.phone ?? ""}
            cepTitular={values["address.zipCode"] ?? ""} numeroEndereco={values["address.number"] ?? ""}
            onDados={setDadosCartao} />
          {contract.installments > 1 && (
            <label className="installments-field">Parcelas
              <select value={installments} onChange={event => { setInstallments(Number(event.target.value)); invalidarSimulacao(); }}>
                {Array.from({ length: contract.installments }, (_, index) => index + 1).map(n => (
                  <option key={n} value={n}>{n}x</option>
                ))}
              </select>
            </label>
          )}
        </div>
      )}

      {contract.segment === "PHYSICAL" && (
        <section className="delivery-section" aria-labelledby="delivery-title">
          <h2 id="delivery-title" className="section-heading">
            <span className="step">{customerDefinesAmount ? 4 : 3}</span>Entrega
          </h2>
          <CamposComprador campos={["address.zipCode", "address.number"]}
            values={values} errors={errors} personType={personType} onChange={change} />
          <small className="field-hint" role="status">
            {consultandoCep ? "Buscando endereço…" : cepResolvido ? "Endereço localizado." : "Informe o CEP para localizar o endereço."}
          </small>
        </section>
      )}

      <div className="coupon-area">
        {!couponVisible ? (
          <button type="button" className="link-button" onClick={() => setCouponVisible(true)}>Adicionar cupom de desconto</button>
        ) : (
          <label htmlFor="coupon"><span className="sr-only">Código do cupom</span>
            <span className="coupon-row">
              <input id="coupon" placeholder="Código do cupom" value={coupon} autoComplete="off" aria-invalid={Boolean(couponError)}
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
      </div>

      <button className="pay-button" type="submit" disabled={submitting || preview} style={{ background: contract.appearance.primaryColor }}>
        {!preview && !submitting && (
          <svg className="pay-button-lock" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <rect x="4" y="10.5" width="16" height="10.5" rx="2.5" /><path d="M8 10.5V7.5a4 4 0 0 1 8 0v3" />
          </svg>
        )}
        {preview ? "Pagamento desativado na pré-visualização" : submitting ? "Processando…" : contract.appearance.buttonText}
      </button>
      <p className="security-note">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M12 2.5 4.5 5.5v6c0 4.7 3.2 8.6 7.5 10 4.3-1.4 7.5-5.3 7.5-10v-6L12 2.5Z" /><path d="m9 12 2 2 4-4" /></svg>
        Compra protegida e processada em ambiente seguro.
      </p>
      <p className="legal-note">Ao clicar no botão acima, você declara que leu e aceita os{" "}
        <a href={contract.legalTexts.termsUrl} target="_blank" rel="noreferrer">termos de uso</a> e a{" "}
        <a href={contract.legalTexts.privacyUrl} target="_blank" rel="noreferrer">política de privacidade</a>.
      </p>
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
