import Link from "next/link";

/** Central de Ajuda: página pública, fora do painel, aberta em uma nova guia pelo menu. */
export default function AjudaLayout({ children }: { children: React.ReactNode }) {
  return <div className="aj">
    <header className="aj-top">
      <div className="aj-wrap aj-bar">
        <Link href="/ajuda" className="aj-brand"><img src="/paysi-logo-negativo.svg" alt="Paysi" /></Link>
        <Link href="/inicio" className="aj-site">Ir para o site</Link>
      </div>
    </header>
    <main className="aj-main" id="conteudo">{children}</main>
    <footer className="aj-foot">
      <div className="aj-wrap">
        <strong>Não encontrou o que buscava? Entre em contato com nossa equipe.</strong>
        <p>Em breve os canais de contato da equipe Paysi estarão aqui.</p>
      </div>
    </footer>
  </div>;
}
