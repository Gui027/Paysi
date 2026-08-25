import { FormEvent, useState } from "react";
import { obterChaveDeIdempotencia } from "../lib/idempotencia";
import { Metodo, SeletorDeMetodo } from "../componentes/SeletorDeMetodo";

export function Formulario() {
  const [metodo, setMetodo] = useState<Metodo>("cartao");

  function enviar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    obterChaveDeIdempotencia();
  }

  return (
    <form className="checkout-form" onSubmit={enviar}>
      <div>
        <span className="step">1</span>
        <h2>Seus dados</h2>
      </div>
      <div className="field-grid">
        <label className="full">Nome completo<input name="nome" autoComplete="name" required /></label>
        <label className="full">E-mail<input name="email" type="email" autoComplete="email" required /></label>
        <label>CPF<input name="documento" inputMode="numeric" autoComplete="off" required /></label>
        <label>Celular<input name="telefone" type="tel" autoComplete="tel" required /></label>
      </div>
      <div className="section-title"><span className="step">2</span><h2>Pagamento</h2></div>
      <SeletorDeMetodo value={metodo} onChange={setMetodo} />
      {metodo === "cartao" && (
        <div className="provider-frame" role="group" aria-label="Dados do cartão">
          <p>O campo seguro do provedor de pagamento será carregado aqui.</p>
          <small>A Paysi nunca recebe os dados do seu cartão.</small>
        </div>
      )}
      <button className="pay-button" type="submit">Finalizar compra</button>
      <p className="security-note">Compra protegida e processada em ambiente seguro.</p>
    </form>
  );
}

