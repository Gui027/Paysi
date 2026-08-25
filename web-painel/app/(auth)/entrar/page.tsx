import Link from "next/link";

export default function EntrarPage() {
  return (
    <div className="auth-shell">
      <aside className="auth-brand">
        <img src="/paysi-logo.svg" alt="Paysi" />
        <div>
          <h1>Seu negócio vende. A Paysi organiza o pagamento.</h1>
          <p>Checkout, divisão automática e afiliados em uma operação financeira rastreável.</p>
        </div>
        <small>Pagamentos inteligentes para o seu negócio.</small>
      </aside>
      <main className="auth-main">
        <form className="auth-card">
          <h2>Entre na sua conta</h2>
          <p>Acesse suas vendas, produtos e saldo.</p>
          <div className="field">
            <label htmlFor="email">E-mail</label>
            <input id="email" name="email" type="email" autoComplete="email" placeholder="voce@empresa.com.br" />
          </div>
          <div className="field">
            <label htmlFor="senha">Senha</label>
            <input id="senha" name="senha" type="password" autoComplete="current-password" placeholder="Sua senha" />
          </div>
          <button className="button button-primary" type="submit">Entrar</button>
          <Link className="subtle-link" href="#">Esqueci minha senha</Link>
        </form>
      </main>
    </div>
  );
}

