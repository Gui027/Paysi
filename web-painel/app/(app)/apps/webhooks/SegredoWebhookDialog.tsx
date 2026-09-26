"use client";

import { useState } from "react";
import { Janela } from "../../../../components/Janela";

/** Mostra o segredo de assinatura uma única vez (criação ou novo segredo). */
export function SegredoWebhookDialog({ secret, title, onClose }: { secret: string; title: string; onClose: () => void }) {
  const [copied, setCopied] = useState(false);

  async function copy() {
    try {
      await navigator.clipboard.writeText(secret);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  }

  return <Janela wide open title={title} onClose={onClose}>
    <p>Copie o segredo e guarde em um local seguro: ele não será mostrado de novo. Use-o no seu servidor para conferir a assinatura do cabeçalho X-Paysi-Signature de cada envio.</p>
    <label className="pe-field"><span>Segredo do webhook</span><input readOnly value={secret} onFocus={event => event.target.select()} /></label>
    {copied && <p className="vd-msg vd-ok" role="status">Segredo copiado.</p>}
    <div className="ui-actions">
      <button type="button" className="ui-button ui-button-secondary" onClick={onClose}>Fechar</button>
      <button type="button" className="ui-button ui-button-primary" onClick={() => void copy()}>Copiar segredo</button>
    </div>
  </Janela>;
}
