"use client";

import Link from "next/link";
import { FormEvent, useState } from "react";
import { AuthBrand } from "../../../components/AuthBrand";
import { requestPasswordRecovery } from "../../../lib/sessao";

export default function RecuperarSenhaPage() {
  const [pending, setPending] = useState(false);
  const [sent, setSent] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;
    const form = new FormData(event.currentTarget);
    setPending(true);
    try {
      await requestPasswordRecovery(String(form.get("email") ?? ""));
    } catch {
      // A resposta visual é deliberadamente idêntica para não enumerar contas.
    } finally {
      setSent(true);
      setPending(false);
    }
  }

  return (
    <div className="auth-shell">
      <AuthBrand />
      <main className="auth-main">
        <form className="auth-card" onSubmit={submit}>
          <h2>Recupere sua senha</h2>
          <p>Informe seu e-mail para receber as próximas instruções.</p>
          {sent ? (
            <div className="success-panel" role="status">
              <strong>Confira seu e-mail</strong>
              <span>Se existir uma conta elegível para esse endereço, enviaremos um link válido por 1 hora.</span>
            </div>
          ) : (
            <>
              <div className="field">
                <label htmlFor="email">E-mail</label>
                <input id="email" name="email" type="email" autoComplete="email" required autoFocus />
              </div>
              <button className="button button-primary" type="submit" disabled={pending}>{pending ? "Enviando…" : "Enviar instruções"}</button>
            </>
          )}
          <Link className="subtle-link" href="/entrar">Voltar para o login</Link>
        </form>
      </main>
    </div>
  );
}
