export type Metodo = "cartao" | "pix" | "boleto";

export function SeletorDeMetodo({ value, onChange }: { value: Metodo; onChange: (value: Metodo) => void }) {
  return (
    <fieldset className="payment-methods">
      <legend>Como você quer pagar?</legend>
      {(["cartao", "pix", "boleto"] as const).map((method) => (
        <label key={method} className={value === method ? "selected" : undefined}>
          <input type="radio" name="metodo" value={method} checked={value === method} onChange={() => onChange(method)} />
          {method === "cartao" ? "Cartão" : method === "pix" ? "Pix" : "Boleto"}
        </label>
      ))}
    </fieldset>
  );
}

