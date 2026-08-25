"use client";

import { useRouter } from "next/navigation";
import { ReactNode, useCallback, useEffect, useState } from "react";
import { ApiRequestError } from "../lib/api";
import { currentSession } from "../lib/sessao";

export function SessionGuard({ children }: { children: ReactNode }) {
  const router = useRouter();
  const [state, setState] = useState<"checking" | "ready" | "error">("checking");

  const verify = useCallback(async () => {
    setState("checking");
    try {
      await currentSession();
      setState("ready");
    } catch (error) {
      if (error instanceof ApiRequestError && error.status === 401) {
        router.replace("/entrar?sessao=expirada");
        return;
      }
      setState("error");
    }
  }, [router]);

  useEffect(() => { void verify(); }, [verify]);

  if (state === "checking") return <div className="auth-loading" role="status">Verificando sua sessão…</div>;
  if (state === "error") return <div className="session-error" role="alert"><p>Não foi possível validar sua sessão.</p><button className="button" type="button" onClick={verify}>Tentar novamente</button></div>;
  return children;
}
