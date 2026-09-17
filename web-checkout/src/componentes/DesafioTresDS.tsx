import { useState } from "react";

/**
 * Sem SDK real do provedor: o desafio verdadeiro abriria um iframe/redirect para o
 * emissor do cartão. Aqui simulamos a confirmação — o token retornado (challengeToken)
 * nunca contém dado do cartão, só a prova de que o desafio foi concluído.
 */
export function DesafioTresDS({ challengeUrl, onConfirmar, confirmando }: {
  challengeUrl: string;
  onConfirmar: (challengeToken: string) => void;
  confirmando: boolean;
}) {
  const [confirmado, setConfirmado] = useState(false);

  return (
    <div className="provider-frame" role="group" aria-label="Confirmação de segurança 3DS">
      <p>Seu banco pede uma confirmação extra para concluir a compra com segurança.</p>
      <a href={challengeUrl} target="_blank" rel="noreferrer" onClick={() => setConfirmado(true)}>
        Abrir confirmação do banco
      </a>
      <button
        type="button"
        className="pay-button"
        disabled={!confirmado || confirmando}
        onClick={() => onConfirmar(`challenge_${crypto.randomUUID()}`)}
      >
        {confirmando ? "Confirmando…" : "Já confirmei no banco"}
      </button>
    </div>
  );
}
