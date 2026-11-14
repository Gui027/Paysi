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
    <div className="success-panel success-confirmation" role="status" aria-live="polite">
      <div className="success-check" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none">
          <path d="m6.5 12.5 3.4 3.4 7.6-8" />
        </svg>
      </div>

      <p className="success-kicker">Compra confirmada</p>
      <h2>Pagamento aprovado!</h2>
      <p className="success-lead">Tudo certo. Seu pagamento foi processado com sucesso.</p>

      <div className="success-email">
        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <rect x="3" y="5" width="18" height="14" rx="2" />
          <path d="m4 7 8 6 8-6" />
        </svg>
        <div>
          <strong>Confirmação por e-mail</strong>
          <span>Enviaremos os detalhes da compra para o e-mail informado.</span>
        </div>
      </div>

      {urlRetorno ? <div className="success-return">
        <p>Voltando para o site em {Math.max(restantes, 0)} segundos…</p>
        <a className="pay-button" href={urlRetorno}>Voltar agora</a>
      </div> : <p className="success-close">Você já pode fechar esta página.</p>}
    </div>
  );
}
