"use client";

import { useEffect, useState } from "react";
import { Janela } from "../../../components/Janela";
import { Skeleton, Toast } from "../../../components/ui";
import { confirmMfaSetup, financeError, MfaEnrollment, setupMfa } from "../../../lib/financeiro";

/**
 * Ativa a verificação em duas etapas (app autenticador). Ela protege trocas de chave Pix, saques altos e a mudança
 * para CNPJ: o vendedor cadastra a chave uma vez e, para mexer nela depois, precisa do código do celular.
 */
export function MfaSetupDialog({ open, onCancel, onEnabled }: { open: boolean; onCancel: () => void; onEnabled: () => void }) {
  const [enrollment, setEnrollment] = useState<MfaEnrollment | null>(null);
  const [code, setCode] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (!open) { setEnrollment(null); setCode(""); setError(null); setSaved(false); return; }
    let active = true;
    setLoading(true);
    setupMfa().then(result => { if (active) setEnrollment(result); })
      .catch(requestError => { if (active) setError(financeError(requestError, "Não foi possível iniciar a ativação. Tente novamente.")); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [open]);

  async function confirm() {
    if (loading || !/^\d{6}$/.test(code)) return;
    setLoading(true);
    setError(null);
    try {
      await confirmMfaSetup(code);
      onEnabled();
    } catch (requestError) {
      setError(financeError(requestError, "Código inválido. Confira os seis dígitos e tente de novo."));
    } finally {
      setLoading(false);
    }
  }

  return <Janela wide open={open} title="Ativar verificação em duas etapas" onClose={() => !loading && onCancel()}>
    <p>Para proteger o seu dinheiro, use um aplicativo autenticador (Google Authenticator, Authy, 1Password…).</p>
    {error && <Toast tone="danger">{error}</Toast>}
    {loading && !enrollment ? <Skeleton label="Gerando o código de ativação" /> : enrollment && <div className="mfa-setup">
      <ol className="mfa-steps">
        <li>No aplicativo, adicione uma conta pela <strong>chave de configuração</strong>: <code className="mfa-secret">{enrollment.secret}</code> <a href={enrollment.otpauthUri}>Abrir no aplicativo</a></li>
        <li>Guarde os códigos de recuperação em um lugar seguro. Cada um funciona uma única vez se você perder o celular:
          <ul className="mfa-recovery">{enrollment.recoveryCodes.map(item => <li key={item}><code>{item}</code></li>)}</ul>
          <label className="mk-terms"><input type="checkbox" checked={saved} onChange={event => setSaved(event.target.checked)} /> Guardei os códigos de recuperação</label>
        </li>
        <li>Digite o código de 6 dígitos que o aplicativo mostra:
          <label className="pe-field"><span className="sr-only">Código de 6 dígitos</span><input inputMode="numeric" autoComplete="one-time-code" maxLength={6} value={code} aria-label="Código de 6 dígitos" onChange={event => setCode(event.target.value.replace(/\D/g, "").slice(0, 6))} /></label>
        </li>
      </ol>
      <div className="ui-actions">
        <button type="button" className="ui-button ui-button-primary" disabled={loading || !saved || code.length !== 6} onClick={() => void confirm()}>{loading ? "Confirmando…" : "Ativar"}</button>
      </div>
    </div>}
  </Janela>;
}
