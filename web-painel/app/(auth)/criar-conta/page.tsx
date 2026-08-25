"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import { AuthBrand } from "../../../components/AuthBrand";
import { ApiRequestError, apiRequest } from "../../../lib/api";

type PersonType = "PF" | "PJ";
type InitialMode = "SELLER" | "AFFILIATE";
type Fields = "fullName" | "email" | "password" | "confirmPassword" | "taxId" | "termsAccepted";
type FieldErrors = Partial<Record<Fields, string>>;

type AccountCreated = {
  accountId: string;
  activeMode: InitialMode;
};

const TERMS_VERSION = "paysi-termos-v1";

function onlyDigits(value: string) {
  return value.replace(/\D/g, "");
}

function formatTaxId(value: string, personType: PersonType) {
  const digits = onlyDigits(value).slice(0, personType === "PF" ? 11 : 14);
  if (personType === "PF") {
    return digits
      .replace(/^(\d{3})(\d)/, "$1.$2")
      .replace(/^(\d{3})\.(\d{3})(\d)/, "$1.$2.$3")
      .replace(/\.(\d{3})(\d)/, ".$1-$2");
  }
  return digits
    .replace(/^(\d{2})(\d)/, "$1.$2")
    .replace(/^(\d{2})\.(\d{3})(\d)/, "$1.$2.$3")
    .replace(/\.(\d{3})(\d)/, ".$1/$2")
    .replace(/(\/\d{4})(\d)/, "$1-$2");
}

async function termsHash() {
  const bytes = new TextEncoder().encode(TERMS_VERSION);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  const hex = Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
  return `sha256:${hex}`;
}

export default function CriarContaPage() {
  const router = useRouter();
  const [personType, setPersonType] = useState<PersonType>("PF");
  const [initialMode, setInitialMode] = useState<InitialMode>("SELLER");
  const [taxId, setTaxId] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;

    const form = new FormData(event.currentTarget);
    const password = String(form.get("password") ?? "");
    const confirmPassword = String(form.get("confirmPassword") ?? "");
    const accepted = form.get("termsAccepted") === "on";
    const nextErrors: FieldErrors = {};

    if (password !== confirmPassword) nextErrors.confirmPassword = "As senhas precisam ser iguais.";
    if (!accepted) nextErrors.termsAccepted = "Você precisa aceitar os termos para continuar.";
    if (Object.keys(nextErrors).length > 0) {
      setErrors(nextErrors);
      return;
    }

    setSubmitting(true);
    setErrors({});
    setFormError("");

    try {
      const created = await apiRequest<AccountCreated>("/v1/accounts", {
        method: "POST",
        body: JSON.stringify({
          fullName: String(form.get("fullName") ?? "").trim(),
          email: String(form.get("email") ?? "").trim(),
          password,
          personType,
          taxId: onlyDigits(taxId),
          initialMode,
          termsHash: await termsHash(),
        }),
      });
      router.replace(`/inicio?modo=${created.activeMode.toLowerCase()}`);
    } catch (error) {
      if (error instanceof ApiRequestError) {
        const apiErrors: FieldErrors = {};
        if (error.problem.field) apiErrors[error.problem.field as Fields] = error.message;
        for (const item of error.problem.fieldErrors ?? []) apiErrors[item.field as Fields] = item.message;
        setErrors(apiErrors);
        if (Object.keys(apiErrors).length === 0) setFormError(error.message);
      } else {
        setFormError("Não foi possível conectar à Paysi. Tente novamente.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="auth-shell">
      <AuthBrand />
      <main className="auth-main auth-main-scroll">
        <form className="auth-card auth-card-wide" onSubmit={submit} noValidate>
          <div className="auth-heading">
            <span className="paysi-rotulo">Comece agora</span>
            <h2>Crie sua conta</h2>
            <p>Informe seus dados para vender ou divulgar produtos na Paysi.</p>
          </div>

          {formError && <div className="form-alert" role="alert">{formError}</div>}

          <div className="field">
            <label htmlFor="fullName">Nome completo ou razão social</label>
            <input id="fullName" name="fullName" autoComplete="name" required aria-invalid={!!errors.fullName} aria-describedby={errors.fullName ? "fullName-error" : undefined} />
            {errors.fullName && <small id="fullName-error" className="field-error">{errors.fullName}</small>}
          </div>

          <div className="field">
            <label htmlFor="email">E-mail</label>
            <input id="email" name="email" type="email" autoComplete="email" required aria-invalid={!!errors.email} aria-describedby={errors.email ? "email-error" : undefined} />
            {errors.email && <small id="email-error" className="field-error">{errors.email}</small>}
          </div>

          <fieldset className="choice-group">
            <legend>Tipo de pessoa</legend>
            <label><input type="radio" name="personType" checked={personType === "PF"} onChange={() => { setPersonType("PF"); setTaxId(""); }} /> Pessoa física</label>
            <label><input type="radio" name="personType" checked={personType === "PJ"} onChange={() => { setPersonType("PJ"); setTaxId(""); }} /> Pessoa jurídica</label>
          </fieldset>

          <div className="field">
            <label htmlFor="taxId">{personType === "PF" ? "CPF" : "CNPJ"}</label>
            <input id="taxId" name="taxId" inputMode="numeric" autoComplete="off" value={taxId} onChange={(event) => setTaxId(formatTaxId(event.target.value, personType))} required aria-invalid={!!errors.taxId} aria-describedby={errors.taxId ? "taxId-error" : undefined} />
            {errors.taxId && <small id="taxId-error" className="field-error">{errors.taxId}</small>}
          </div>

          <div className="field-grid">
            <div className="field">
              <label htmlFor="password">Senha</label>
              <input id="password" name="password" type={showPassword ? "text" : "password"} autoComplete="new-password" minLength={8} maxLength={128} required aria-invalid={!!errors.password} />
              {errors.password && <small className="field-error">{errors.password}</small>}
            </div>
            <div className="field">
              <label htmlFor="confirmPassword">Confirme a senha</label>
              <input id="confirmPassword" name="confirmPassword" type={showPassword ? "text" : "password"} autoComplete="new-password" minLength={8} maxLength={128} required aria-invalid={!!errors.confirmPassword} aria-describedby={errors.confirmPassword ? "confirmPassword-error" : undefined} />
              {errors.confirmPassword && <small id="confirmPassword-error" className="field-error">{errors.confirmPassword}</small>}
            </div>
          </div>
          <label className="inline-check"><input type="checkbox" checked={showPassword} onChange={(event) => setShowPassword(event.target.checked)} /> Mostrar senhas</label>

          <fieldset className="choice-group choice-stack">
            <legend>Como você quer começar?</legend>
            <label><input type="radio" name="initialMode" checked={initialMode === "SELLER"} onChange={() => setInitialMode("SELLER")} /> Quero vender meus produtos</label>
            <label><input type="radio" name="initialMode" checked={initialMode === "AFFILIATE"} onChange={() => setInitialMode("AFFILIATE")} /> Quero divulgar como afiliado</label>
          </fieldset>

          <div className="legal-note">
            A Paysi organiza pagamentos e repasses por meio de provedores parceiros. A abertura da conta não representa conta bancária nem aprovação automática de KYC.
          </div>
          <label className="inline-check terms-check">
            <input name="termsAccepted" type="checkbox" aria-invalid={!!errors.termsAccepted} aria-describedby={errors.termsAccepted ? "terms-error" : undefined} />
            <span>Li e aceito os Termos de Uso e a Política de Privacidade.</span>
          </label>
          {errors.termsAccepted && <small id="terms-error" className="field-error block-error">{errors.termsAccepted}</small>}

          <button className="button button-primary" type="submit" disabled={submitting}>{submitting ? "Criando conta…" : "Criar conta"}</button>
          <p className="auth-footer">Já tem uma conta? <Link href="/entrar">Entrar</Link></p>
        </form>
      </main>
    </div>
  );
}
