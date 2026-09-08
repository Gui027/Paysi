import { useEffect, useState } from "react";
import { ResumoDaOferta } from "./componentes/ResumoDaOferta";
import { CheckoutForm } from "./telas/CheckoutForm";
import { ApiRequestError } from "./lib/api";
import { CheckoutContract, getCheckoutContract } from "./lib/checkout";

function slugFromLocation(): string | null {
  const path = window.location.pathname.replace(/^\/+|\/+$/g, "");
  return path.length > 0 ? path.split("/")[0] : null;
}

export function App() {
  const [slug] = useState(slugFromLocation);
  const [contract, setContract] = useState<CheckoutContract | null>(null);
  const [loading, setLoading] = useState(Boolean(slug));
  const [notFound, setNotFound] = useState(false);
  const [loadFailed, setLoadFailed] = useState(false);

  useEffect(() => {
    if (!slug) return;
    let active = true;
    getCheckoutContract(slug)
      .then(loaded => { if (active) setContract(loaded); })
      .catch(error => {
        if (!active) return;
        if (error instanceof ApiRequestError && error.status === 404) setNotFound(true);
        else setLoadFailed(true);
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [slug]);

  if (!slug) return <EstadoVazio titulo="Link inválido" descricao="Nenhuma oferta foi especificada nesta URL." />;
  if (loading) return <div className="loading-state" role="status">Carregando…</div>;
  if (notFound) return <EstadoVazio titulo="Oferta não encontrada" descricao="Esta oferta não está disponível para compra." />;
  if (loadFailed || !contract) return <EstadoVazio titulo="Não foi possível carregar" descricao="Tente novamente em instantes." />;

  return (
    <main className="checkout-shell">
      <ResumoDaOferta contract={contract} />
      <section className="form-column">
        <CheckoutForm slug={slug} contract={contract} />
        <footer>
          Pagamento processado por <img src="/paysi-logo.svg" alt="Paysi" />
          <span>A Paysi não é uma instituição autorizada a funcionar pelo Banco Central.</span>
        </footer>
      </section>
    </main>
  );
}

function EstadoVazio({ titulo, descricao }: { titulo: string; descricao: string }) {
  return (
    <div className="empty-state" role="status">
      <h1>{titulo}</h1>
      <p>{descricao}</p>
    </div>
  );
}
