"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { FormEvent, Suspense, useState } from "react";
import { AuthBrand } from "../../../components/AuthBrand";
import { ApiRequestError } from "../../../lib/api";
import { adminLogin } from "../../../lib/admin-dashboard";
import { login } from "../../../lib/sessao";

function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [showPassword, setShowPassword] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const expired = searchParams.get("sessao") === "expirada";

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;
    const form = new FormData(event.currentTarget);
    const identifier = String(form.get("identifier") ?? "").trim();
    const password = String(form.get("password") ?? "");
    setPending(true);
    setError("");
    try {
      if (identifier.toLowerCase() === "admin") {
        await adminLogin(identifier, password);
        router.replace("/admin");
        router.refresh();
        return;
      }
      const session = await login(identifier, password);
      router.replace(`/inicio?modo=${session.activeMode.toLowerCase()}`);
      router.refresh();
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError && requestError.status === 429
        ? "Muitas tentativas. Aguarde alguns minutos e tente novamente."
        : requestError instanceof ApiRequestError && requestError.status >= 500
          ? "A Paysi está indisponível no momento. Tente novamente."
          : "E-mail, usuário ou senha inválidos.");
    } finally {
      setPending(false);
    }
  }

  return (
    <div className="auth-shell">
      <AuthBrand />
      <main className="auth-main">
        <form className="auth-card" onSubmit={submit}>
          <h2>Entre na sua conta</h2>
          <p>Acesse suas vendas, produtos e saldo.</p>
          {expired && <div className="form-alert" role="status">Sua sessão expirou por inatividade. Entre novamente.</div>}
          {error && <div className="form-alert" role="alert">{error}</div>}
          <div className="field">
            <label htmlFor="identifier">E-mail ou usuário</label>
            <input id="identifier" name="identifier" type="text" autoComplete="username" placeholder="voce@empresa.com.br" required />
          </div>
          <div className="field">
            <label htmlFor="senha">Senha</label>
            <input id="senha" name="password" type={showPassword ? "text" : "password"} autoComplete="current-password" placeholder="Sua senha" required />
          </div>
          <label className="inline-check"><input type="checkbox" checked={showPassword} onChange={(event) => setShowPassword(event.target.checked)} /> Exibir caracteres</label>
          <button className="button button-primary" type="submit" disabled={pending}>{pending ? "Entrando…" : "Entrar"}</button>
          <div className="auth-links">
            <Link className="subtle-link" href="/recuperar-senha">Esqueci minha senha</Link>
            <Link className="subtle-link" href="/criar-conta">Criar uma conta</Link>
          </div>
        </form>
      </main>
    </div>
  );
}

export default function EntrarPage() {
  return <Suspense fallback={<div className="auth-loading">Carregando…</div>}><LoginForm /></Suspense>;
}
