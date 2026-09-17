import { useEffect, useRef, useState } from "react";
import { consultarStatusCobranca, statusFinalFalhou, statusFinalPago } from "./statusCobranca.js";

/**
 * Consulta o status da cobrança com backoff (5s, 8s, 13s… até 30s) e para sozinho
 * quando chega a um estado final ou quando o componente desmonta — nunca continua
 * pedindo depois que a tela some.
 */
export function usePollingStatus(chargeId: string, statusInicial: string) {
  const [status, setStatus] = useState(statusInicial);
  const [erro, setErro] = useState<string | null>(null);
  const montado = useRef(true);

  useEffect(() => {
    montado.current = true;
    let atraso = 5000;
    let timer: ReturnType<typeof setTimeout>;

    async function consultar() {
      try {
        const atual = await consultarStatusCobranca(chargeId);
        if (!montado.current) return;
        setStatus(atual.status);
        if (statusFinalPago(atual.status) || statusFinalFalhou(atual.status)) return;
      } catch {
        if (!montado.current) return;
        setErro("Não foi possível confirmar o status agora. Continuaremos tentando.");
      }
      if (!montado.current) return;
      atraso = Math.min(atraso * 1.6, 30000);
      timer = setTimeout(() => void consultar(), atraso);
    }

    timer = setTimeout(() => void consultar(), atraso);
    return () => {
      montado.current = false;
      clearTimeout(timer);
    };
  }, [chargeId]);

  return { status, erro };
}
