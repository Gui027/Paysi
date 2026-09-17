import { useState } from "react";
import { Campo } from "./Campo";
import {
  cvvValido,
  formatarNumeroCartao,
  formatarValidade,
  gerarTokenCartao,
  numeroCartaoValido,
  validadeValida,
} from "../lib/cartao";

/**
 * Sem SDK real do provedor de pagamento neste ambiente: estes campos simulam o que
 * o iframe/JS do provedor faria — número e CVV nunca saem deste componente. Só o
 * token opaco resultante (onToken) chega ao restante do formulário e ao backend.
 */
export function CampoDeCartao({ onToken }: { onToken: (token: string | null) => void }) {
  const [numero, setNumero] = useState("");
  const [validade, setValidade] = useState("");
  const [cvv, setCvv] = useState("");
  const [nome, setNome] = useState("");
  const [tocado, setTocado] = useState(false);

  function atualizar(campo: "numero" | "validade" | "cvv" | "nome", valor: string) {
    const proximos = {
      numero: campo === "numero" ? formatarNumeroCartao(valor) : numero,
      validade: campo === "validade" ? formatarValidade(valor) : validade,
      cvv: campo === "cvv" ? valor.replace(/\D/g, "").slice(0, 4) : cvv,
      nome: campo === "nome" ? valor : nome,
    };
    setNumero(proximos.numero);
    setValidade(proximos.validade);
    setCvv(proximos.cvv);
    setNome(proximos.nome);
    setTocado(true);

    const completo = numeroCartaoValido(proximos.numero) && validadeValida(proximos.validade)
      && cvvValido(proximos.cvv) && proximos.nome.trim().length > 1;
    onToken(completo ? gerarTokenCartao() : null);
  }

  const numeroErro = tocado && numero && !numeroCartaoValido(numero) ? "Número de cartão inválido." : undefined;
  const validadeErro = tocado && validade && !validadeValida(validade) ? "Validade inválida ou vencida." : undefined;
  const cvvErro = tocado && cvv && !cvvValido(cvv) ? "CVV inválido." : undefined;

  return (
    <div className="provider-frame" role="group" aria-label="Dados do cartão">
      <Campo id="cartao-nome" label="Nome impresso no cartão" full value={nome}
        onChange={event => atualizar("nome", event.target.value)} autoComplete="cc-name" />
      <Campo id="cartao-numero" label="Número do cartão" full inputMode="numeric" value={numero}
        onChange={event => atualizar("numero", event.target.value)} error={numeroErro} autoComplete="cc-number" />
      <Campo id="cartao-validade" label="Validade (MM/AA)" inputMode="numeric" value={validade}
        onChange={event => atualizar("validade", event.target.value)} error={validadeErro} autoComplete="cc-exp" />
      <Campo id="cartao-cvv" label="CVV" inputMode="numeric" value={cvv} type="password"
        onChange={event => atualizar("cvv", event.target.value)} error={cvvErro} autoComplete="cc-csc" />
      <small>A Paysi nunca recebe o número nem o CVV do seu cartão.</small>
    </div>
  );
}
