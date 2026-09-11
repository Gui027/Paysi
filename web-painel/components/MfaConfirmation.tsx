"use client";

import { useEffect, useState } from "react";
import { ApiRequestError } from "../lib/api";
import { createMfaChallenge, MfaChallenge, MfaOperation, verifyMfaChallenge } from "../lib/payout";
import { Botao, Campo, Dialog, Skeleton, Toast } from "./ui";

export function MfaConfirmation({ open, operation, onCancel, onVerified }: {
  open: boolean;
  operation: MfaOperation;
  onCancel: () => void;
  onVerified: (challengeId: string) => Promise<void>;
}) {
  const [challenge, setChallenge] = useState<MfaChallenge | null>(null);
  const [code, setCode] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function newChallenge() {
    setLoading(true);
    setError(null);
    setCode("");
    try {
      setChallenge(await createMfaChallenge(operation));
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError && requestError.problem.code === "MFA_REQUIRED"
        ? "Ative o segundo fator na sua conta antes de continuar."
        : "Não foi possível solicitar o código de segurança.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (open) void newChallenge();
    else { setChallenge(null); setCode(""); setError(null); }
    // A abertura do diálogo inicia exatamente um desafio; novas tentativas usam o botão dedicado.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, operation]);

  async function confirm() {
    if (!challenge || loading || !/^\d{6}$/.test(code)) return;
    setLoading(true);
    setError(null);
    try {
      await verifyMfaChallenge(challenge.challengeId, code);
      await onVerified(challenge.challengeId);
    } catch (requestError) {
      const expired = requestError instanceof ApiRequestError && requestError.problem.code === "MFA_CHALLENGE_INVALID";
      setError(expired ? "O desafio expirou ou já foi usado. Gere um novo código para tentar novamente." : requestError instanceof ApiRequestError && requestError.problem.code === "MFA_CODE_INVALID" ? "Código inválido. Confira os seis dígitos." : "Não foi possível confirmar o código de segurança.");
      if (expired) setChallenge(null);
    } finally {
      setLoading(false);
    }
  }

  return <Dialog open={open} title="Confirmação de segurança" onClose={() => !loading && onCancel()}>
    <p>Digite o código de seis dígitos do seu aplicativo autenticador. O desafio expira em cinco minutos.</p>
    {error && <Toast tone="danger">{error}</Toast>}
    {loading && !challenge ? <Skeleton label="Solicitando desafio de segurança" /> : challenge ? <>
      <Campo label="Código MFA" value={code} inputMode="numeric" autoComplete="one-time-code" maxLength={6} disabled={loading} error={code.length > 0 && !/^\d{6}$/.test(code) ? "Digite os seis dígitos." : undefined} onChange={event => setCode(event.target.value.replace(/\D/g, "").slice(0, 6))} />
      <div className="ui-actions"><Botao disabled={loading || !/^\d{6}$/.test(code)} onClick={() => void confirm()}>{loading ? "Confirmando…" : "Confirmar código"}</Botao></div>
    </> : <div className="ui-actions"><Botao disabled={loading} onClick={() => void newChallenge()}>Gerar novo desafio</Botao></div>}
  </Dialog>;
}
