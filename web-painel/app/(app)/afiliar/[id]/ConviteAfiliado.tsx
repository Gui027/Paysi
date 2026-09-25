"use client";

import Link from "next/link";
import { useState } from "react";
import { ApiRequestError } from "../../../../lib/api";
import { Affiliation, formatCommissionBps, requestAffiliation } from "../../../../lib/afiliados";

// Destino do link de convite que o vendedor compartilha: o convidado entra (ou cria a conta)
// e pede a afiliação ao produto. Se o programa aprova automaticamente, já sai aprovado.
export function ConviteAfiliado({ productId }: { productId: string }) {
  const [result, setResult] = useState<Affiliation | null>(null);
  const [error, setError] = useState<{ code?: string; message: string } | null>(null);
  const [sending, setSending] = useState(false);

  async function request() {
    setSending(true);
    setError(null);
    try {
      setResult(await requestAffiliation(productId));
    } catch (requestError) {
      if (requestError instanceof ApiRequestError) setError({ code: requestError.problem.code, message: requestError.message });
      else setError({ message: "Não foi possível enviar o pedido. Tente novamente." });
    } finally {
      setSending(false);
    }
  }

  const approved = result?.status === "APPROVED";
  return <div className="convite">
    <section className="dash-card convite-card">
      <h1>Convite para ser afiliado</h1>
      {!result && <>
        <p>Você foi convidado para divulgar um produto na Paysi e receber comissão por cada venda que trouxer.</p>
        {error && <p className="pe-error" role="alert">{error.message}{error.code === "AFFILIATE_KYC_REQUIRED" && <> <Link href="/verificacao">Verificar minha conta</Link></>}</p>}
        <button type="button" className="ui-button ui-button-primary" disabled={sending} onClick={() => void request()}>{sending ? "Enviando…" : "Quero ser afiliado"}</button>
      </>}
      {result && <>
        <p role="status">{approved
          ? `Afiliação aprovada com comissão de ${formatCommissionBps(result.commissionBps)}. Seu link de divulgação já está disponível.`
          : "Pedido enviado. O vendedor vai analisar e você será avisado na aprovação."}</p>
        <Link className="ui-button ui-button-primary" href={approved ? "/meus-links" : "/inicio"}>{approved ? "Ver meus links" : "Ir para o dashboard"}</Link>
      </>}
    </section>
  </div>;
}
