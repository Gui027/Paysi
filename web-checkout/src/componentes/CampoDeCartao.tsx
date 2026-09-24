import { useState } from "react";
import { Campo } from "./Campo";
import {
  CartaoDigitado,
  cartaoCompleto,
  cepValido,
  cvvValido,
  DadosCartao,
  formatarCep,
  formatarNumeroCartao,
  formatarValidade,
  montarDadosCartao,
  numeroCartaoValido,
  telefoneValido,
  validadeValida,
} from "../lib/cartao";

const vazio: CartaoDigitado = { nome: "", numero: "", validade: "", cvv: "", cep: "", numeroEndereco: "", telefone: "" };

/**
 * Coleta os dados do cartão e do titular. Ficam só em memória: quando o formulário está
 * completo entregam {@link DadosCartao} ao pai, que os envia ao backend para tokenizar
 * (depois do pedido criado). Nada disso é guardado no navegador nem persistido pelo Paysi.
 */
export function CampoDeCartao({ onDados }: { onDados: (dados: DadosCartao | null) => void }) {
  const [c, setC] = useState<CartaoDigitado>(vazio);
  const [tocado, setTocado] = useState(false);

  function atualizar(campo: keyof CartaoDigitado, valor: string) {
    const proximo: CartaoDigitado = { ...c, [campo]:
      campo === "numero" ? formatarNumeroCartao(valor)
        : campo === "validade" ? formatarValidade(valor)
          : campo === "cvv" ? valor.replace(/\D/g, "").slice(0, 4)
            : campo === "cep" ? formatarCep(valor)
              : campo === "telefone" ? valor.replace(/[^\d()\s-]/g, "").slice(0, 15)
                : valor };
    setC(proximo);
    setTocado(true);
    onDados(cartaoCompleto(proximo) ? montarDadosCartao(proximo) : null);
  }

  const erro = (mostrar: boolean, mensagem: string) => (tocado && mostrar ? mensagem : undefined);

  return (
    <div className="provider-frame" role="group" aria-label="Dados do cartão">
      <div className="field-grid" style={{ margin: 0 }}>
      <Campo id="cartao-nome" label="Nome impresso no cartão" full value={c.nome}
        onChange={event => atualizar("nome", event.target.value)} autoComplete="cc-name" />
      <Campo id="cartao-numero" label="Número do cartão" full inputMode="numeric" value={c.numero}
        onChange={event => atualizar("numero", event.target.value)} autoComplete="cc-number"
        error={erro(Boolean(c.numero) && !numeroCartaoValido(c.numero), "Número de cartão inválido.")} />
      <Campo id="cartao-validade" label="Validade (MM/AA)" inputMode="numeric" value={c.validade}
        onChange={event => atualizar("validade", event.target.value)} autoComplete="cc-exp"
        error={erro(Boolean(c.validade) && !validadeValida(c.validade), "Validade inválida ou vencida.")} />
      <Campo id="cartao-cvv" label="CVV" inputMode="numeric" value={c.cvv} type="password"
        onChange={event => atualizar("cvv", event.target.value)} autoComplete="cc-csc"
        error={erro(Boolean(c.cvv) && !cvvValido(c.cvv), "CVV inválido.")} />
      <Campo id="cartao-cep" label="CEP do titular" inputMode="numeric" value={c.cep}
        onChange={event => atualizar("cep", event.target.value)} autoComplete="postal-code"
        error={erro(Boolean(c.cep) && !cepValido(c.cep), "CEP inválido.")} />
      <Campo id="cartao-endereco-numero" label="Número do endereço" value={c.numeroEndereco}
        onChange={event => atualizar("numeroEndereco", event.target.value.slice(0, 10))} autoComplete="off" />
      <Campo id="cartao-telefone" label="Telefone com DDD" full inputMode="tel" value={c.telefone}
        onChange={event => atualizar("telefone", event.target.value)} autoComplete="tel"
        error={erro(Boolean(c.telefone) && !telefoneValido(c.telefone), "Telefone inválido.")} />
      </div>
      <small style={{ display: "block", marginTop: "var(--esp-3)" }}>Seus dados de cartão viajam por conexão segura, são usados só para autorizar este pagamento e não ficam guardados na Paysi.</small>
    </div>
  );
}
