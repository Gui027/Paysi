"use client";

import Link from "next/link";
import { FormEvent, useState } from "react";
import { ApiRequestError } from "../../../lib/api";
import { resetPassword } from "../../../lib/sessao";

export function RedefinirSenhaForm({ token }: { token: string }) {
  const [pending, setPending] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;
    const form = new FormData(event.currentTarget);
    const password = String(form.get("newPassword") ?? "");
    const confirmation = String(form.get("confirmPassword") ?? "");
    if (password !== confirmation) {
      setError("As senhas precisam ser iguais.");
      return;
    }
    setPending(true);
    setError("");
    try {
      await resetPassword(token, password, confirmation);
      setDone(true);
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError && requestError.status >= 500
        ? "Não foi possível redefinir agora. Tente novamente."
        : "Este link é inválido, expirou ou já foi utilizado.");
    } finally {
      setPending(false);
    }
  }

  if (!token) return <div className="form-alert" role="alert">Link de recuperação inválido. Solicite um novo link.</div>;
  if (done) return <div className="success-panel" role="status"><strong>Senha redefinida</strong><span>Você já pode entrar usando a nova senha.</span><Link className="subtle-link" href="/entrar">Ir para o login</Link></div>;

  return (
    <form onSubmit={submit}>
      {error && <div className="form-alert" role="alert">{error}</div>}
      <div className="field">
        <label htmlFor="newPassword">Nova senha</label>
        <input id="newPassword" name="newPassword" type="password" autoComplete="new-password" minLength={8} maxLength={128} required autoFocus />
      </div>
      <div className="field">
        <label htmlFor="confirmPassword">Confirme a nova senha</label>
        <input id="confirmPassword" name="confirmPassword" type="password" autoComplete="new-password" minLength={8} maxLength={128} required />
      </div>
      <button className="button button-primary" type="submit" disabled={pending}>{pending ? "Redefinindo…" : "Redefinir senha"}</button>
    </form>
  );
}
