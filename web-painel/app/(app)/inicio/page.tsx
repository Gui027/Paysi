export const metadata = { title: "Início" };

export default function InicioPage() {
  return (
    <>
      <header className="content-header">
        <div>
          <h1>Olá, equipe Paysi</h1>
          <p>Acompanhe a operação em um só lugar.</p>
        </div>
        <span className="badge">Ambiente local</span>
      </header>
      <section className="stats" aria-label="Resumo financeiro">
        <article className="card"><span>Vendas hoje</span><strong className="paysi-valor">R$ 0,00</strong></article>
        <article className="card"><span>Saldo disponível</span><strong className="paysi-valor">R$ 0,00</strong></article>
        <article className="card"><span>Em garantia</span><strong className="paysi-valor">R$ 0,00</strong></article>
      </section>
      <section className="card empty">
        <div>
          <h2>Nenhuma venda hoje ainda</h2>
          <p>Cadastre o primeiro produto e publique uma oferta.</p>
        </div>
      </section>
    </>
  );
}

