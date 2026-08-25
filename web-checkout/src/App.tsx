import { ResumoDaOferta } from "./componentes/ResumoDaOferta";
import { Formulario } from "./telas/Formulario";

const ofertaInicial = {
  nome: "Produto de demonstração",
  descricao: "A estrutura do checkout está pronta para receber uma oferta pública da API.",
  vendedor: "Loja de demonstração",
  valorCentavos: 17_700,
};

export function App() {
  return (
    <main className="checkout-shell">
      <ResumoDaOferta {...ofertaInicial} />
      <section className="form-column">
        <Formulario />
        <footer>
          Pagamento processado por <img src="/paysi-logo.svg" alt="Paysi" />
          <span>A Paysi não é uma instituição autorizada a funcionar pelo Banco Central.</span>
        </footer>
      </section>
    </main>
  );
}

