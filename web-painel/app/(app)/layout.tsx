import Link from "next/link";

export default function AppLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <img src="/paysi-logo.svg" alt="Paysi" />
        <nav aria-label="Navegação principal">
          <Link href="/inicio" aria-current="page">Início</Link>
          <Link href="#">Produtos</Link>
          <Link href="#">Vendas</Link>
          <Link href="#">Assinaturas</Link>
          <Link href="#">Afiliados</Link>
          <Link href="#">Saldo</Link>
        </nav>
      </aside>
      <main className="content">{children}</main>
    </div>
  );
}

