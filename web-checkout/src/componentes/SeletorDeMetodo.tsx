export type Metodo = "cartao" | "pix" | "boleto";

const TODOS_OS_METODOS: readonly Metodo[] = ["cartao", "pix", "boleto"];

export function SeletorDeMetodo({ value, onChange, disponiveis = TODOS_OS_METODOS }: {
  value: Metodo;
  onChange: (value: Metodo) => void;
  disponiveis?: readonly Metodo[];
}) {
  return (
    <fieldset className="payment-methods">
      <legend>Como você quer pagar?</legend>
      {disponiveis.map((method) => (
        <label key={method} className={value === method ? "selected" : undefined}>
          <input type="radio" name="metodo" value={method} checked={value === method} onChange={() => onChange(method)} />
          {method === "cartao" ? "Cartão" : method === "pix" ? "Pix" : "Boleto"}
        </label>
      ))}
    </fieldset>
  );
}

