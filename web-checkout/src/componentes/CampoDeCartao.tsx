import { useEffect, useState } from "react";
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
  validadeValida,
} from "../lib/cartao";

const vazio: CartaoDigitado = { numero: "", validade: "", cvv: "", cep: "", numeroEndereco: "" };

/**
 * Coleta os dados do cartão e do titular. Ficam só em memória: quando o formulário está
 * completo entregam {@link DadosCartao} ao pai, que os envia ao backend para tokenizar
 * (depois do pedido criado). Nada disso é guardado no navegador nem persistido pelo Paysi.
 */
export function CampoDeCartao({ nomeTitular, telefone, onDados }: { nomeTitular: string; telefone: string; onDados: (dados: DadosCartao | null) => void }) {
  const [c, setC] = useState<CartaoDigitado>(vazio);
  const [tocado, setTocado] = useState(false);

  function atualizar(campo: keyof CartaoDigitado, valor: string) {
    const proximo: CartaoDigitado = { ...c, [campo]:
      campo === "numero" ? formatarNumeroCartao(valor)
        : campo === "validade" ? formatarValidade(valor)
          : campo === "cvv" ? valor.replace(/\D/g, "").slice(0, 4)
            : campo === "cep" ? formatarCep(valor)
              : valor };
    setC(proximo);
    setTocado(true);
    onDados(cartaoCompleto(proximo, nomeTitular, telefone) ? montarDadosCartao(proximo, nomeTitular, telefone) : null);
  }

  useEffect(() => {
    onDados(cartaoCompleto(c, nomeTitular, telefone) ? montarDadosCartao(c, nomeTitular, telefone) : null);
  }, [nomeTitular, telefone]);

  const erro = (mostrar: boolean, mensagem: string) => (tocado && mostrar ? mensagem : undefined);

  return (
    <div className="provider-frame" role="group" aria-label="Dados do cartão">
      <div className="field-grid" style={{ margin: 0 }}>
      <Campo id="cartao-numero" label="Número do cartão" hideLabel placeholder="Número do cartão" full inputMode="numeric" value={c.numero}
        onChange={event => atualizar("numero", event.target.value)} autoComplete="cc-number"
        error={erro(Boolean(c.numero) && !numeroCartaoValido(c.numero), "Número de cartão inválido.")} />
      <Campo id="cartao-validade" label="Validade (MM/AA)" hideLabel placeholder="Validade (MM/AA)" inputMode="numeric" value={c.validade}
        onChange={event => atualizar("validade", event.target.value)} autoComplete="cc-exp"
        error={erro(Boolean(c.validade) && !validadeValida(c.validade), "Validade inválida ou vencida.")} />
      <Campo id="cartao-cvv" label="Cód. segurança" hideLabel placeholder="Cód. segurança" inputMode="numeric" value={c.cvv} type="password"
        onChange={event => atualizar("cvv", event.target.value)} autoComplete="cc-csc"
        error={erro(Boolean(c.cvv) && !cvvValido(c.cvv), "CVV inválido.")} />
      <Campo id="cartao-cep" label="CEP do titular" hideLabel placeholder="CEP" inputMode="numeric" value={c.cep}
        onChange={event => atualizar("cep", event.target.value)} autoComplete="postal-code"
        error={erro(Boolean(c.cep) && !cepValido(c.cep), "CEP inválido.")} />
      <Campo id="cartao-endereco-numero" label="Número do endereço" hideLabel placeholder="Número" value={c.numeroEndereco}
        onChange={event => atualizar("numeroEndereco", event.target.value.slice(0, 10))} autoComplete="off" />
      </div>
    </div>
  );
}
