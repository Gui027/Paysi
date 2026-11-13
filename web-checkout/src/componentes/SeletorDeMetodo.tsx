import { ReactElement } from "react";
import { IconeBoleto, IconeCartao, IconePix } from "./IconesDePagamento";

export type Metodo = "cartao" | "pix" | "boleto";

const TODOS_OS_METODOS: readonly Metodo[] = ["cartao", "pix", "boleto"];

const META: Record<Metodo, { rotulo: string; icone: () => ReactElement }> = {
  cartao: { rotulo: "Cartão", icone: IconeCartao },
  pix: { rotulo: "Pix", icone: IconePix },
  boleto: { rotulo: "Boleto", icone: IconeBoleto },
};

export function SeletorDeMetodo({ value, onChange, disponiveis = TODOS_OS_METODOS }: {
  value: Metodo;
  onChange: (value: Metodo) => void;
  disponiveis?: readonly Metodo[];
}) {
  return (
    <fieldset className="payment-methods">
      <legend>Como você quer pagar?</legend>
      {disponiveis.map((method) => {
        const { rotulo, icone: Icone } = META[method];
        return (
          <label key={method} className={value === method ? "selected" : undefined}>
            <input type="radio" name="metodo" value={method} checked={value === method} onChange={() => onChange(method)} aria-label={rotulo} />
            <span className="payment-icon-wrap"><Icone /></span>
            <span className="payment-method-label">{rotulo}</span>
          </label>
        );
      })}
    </fieldset>
  );
}
