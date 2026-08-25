import { formatarCentavos } from "../lib/formato";

interface Props {
  nome: string;
  descricao: string;
  vendedor: string;
  valorCentavos: number;
}

export function ResumoDaOferta({ nome, descricao, vendedor, valorCentavos }: Props) {
  return (
    <aside className="offer-summary">
      <img className="merchant-mark" src="/paysi-logo.svg" alt="Paysi" />
      <span className="eyebrow">Você está comprando</span>
      <h1>{nome}</h1>
      <p>{descricao}</p>
      <div className="price paysi-valor">{formatarCentavos(valorCentavos)}</div>
      <small>Vendido por {vendedor}</small>
    </aside>
  );
}

