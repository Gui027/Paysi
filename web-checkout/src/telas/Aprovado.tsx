import { useEffect, useState } from "react";

const SEGUNDOS_ATE_VOLTAR = 6;

/**
 * Com URL de retorno (o vendedor integrou o checkout ao sistema dele), o comprador volta sozinho
 * depois de alguns segundos; o botão cobre quem não quer esperar. O retorno é só navegação:
 * o sistema do vendedor deve confirmar a venda pelo webhook, nunca por esta URL.
 */
export function Aprovado({ urlRetorno = null }: { urlRetorno?: string | null }) {
  const [restantes, setRestantes] = useState(SEGUNDOS_ATE_VOLTAR);

  useEffect(() => {
    if (!urlRetorno) return;
    if (restantes <= 0) {
      window.location.assign(urlRetorno);
      return;
    }
    const timer = window.setTimeout(() => setRestantes(value => value - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [urlRetorno, restantes]);

  return (
    <div className="success-panel" role="status">
      <h2>Pagamento aprovado</h2>
      <p>Você receberá a confirmação e a nota fiscal por e-mail.</p>
      {urlRetorno && <>
        <p>Você será levado de volta em {Math.max(restantes, 0)} s.</p>
        <a className="pay-button" href={urlRetorno}>Voltar agora</a>
      </>}
    </div>
  );
}
