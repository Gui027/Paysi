import Link from "next/link";
import { eventCatalog } from "../../../lib/webhooks";

export const metadata = { title: "Webhooks da Paysi" };

const verify = `import crypto from "node:crypto";

// rawBody: o corpo EXATO recebido (texto), sem reformatar o JSON.
function assinaturaValida(rawBody, headers, segredo) {
  const timestamp = headers["x-paysi-timestamp"];
  const esperado = "v1=" + crypto.createHmac("sha256", segredo)
    .update(timestamp + "." + rawBody)
    .digest("hex");
  const recebido = headers["x-paysi-signature"] ?? "";
  return recebido.length === esperado.length &&
    crypto.timingSafeEqual(Buffer.from(recebido), Buffer.from(esperado));
}`;

const example = `{
  "eventId": "8f0c2a1e-9d1b-4a52-8a3e-0f4c8b1d7a10",
  "type": "PAYMENT.APPROVED",
  "createdAt": "2026-09-26T14:02:11Z",
  "data": {
    "chargeId": "1c9c6d0e-...",
    "reference": "cliente-123",
    "productId": "b1f0...",
    "productName": "Meu curso",
    "offerName": "Plano mensal",
    "amountCents": 4990,
    "currency": "BRL",
    "method": "PIX",
    "subscriptionId": null,
    "cycleNumber": 1,
    "paidAt": "2026-09-26T14:02:10Z",
    "buyer": { "name": "Ana Souza", "email": "ana@exemplo.com" }
  }
}`;

export default function Page() {
  return <div className="aj-wrap aj-doc">
    <nav aria-label="Você está em"><Link href="/ajuda">Central de Ajuda</Link> <span aria-hidden="true">›</span> <span>Webhooks</span></nav>
    <h1>Webhooks da Paysi</h1>
    <p>Um webhook avisa o seu sistema, na hora, quando algo acontece: uma venda aprovada, um reembolso, uma assinatura cancelada. A Paysi faz um <code>POST</code> em JSON para a URL que você cadastrar.</p>

    <h2>1. Crie o webhook</h2>
    <p>Em <strong>Apps → Webhooks → Criar webhook</strong>, informe o nome, a URL (só HTTPS, porta padrão), o produto (ou todos) e os eventos. Use <strong>Testar Webhook</strong> para conferir se a URL responde. Ao criar, copie o segredo: ele aparece uma única vez.</p>

    <h2>2. Eventos</h2>
    <table>
      <caption className="sr-only">Eventos de webhook</caption>
      <thead><tr><th scope="col">Evento</th><th scope="col">Valor de <code>type</code></th></tr></thead>
      <tbody>{eventCatalog.map(item => <tr key={item.key}><td>{item.label}</td><td>{item.types.map(type => <code key={type}>{type} </code>)}</td></tr>)}</tbody>
    </table>

    <h2>3. O que você recebe</h2>
    <p>Todo envio tem o mesmo envelope: <code>eventId</code> (fixo, use para não processar duas vezes), <code>type</code>, <code>createdAt</code> e <code>data</code>. Valores em dinheiro ficam em centavos. Se o comprador chegou pelo link com <code>?ref=</code>, o <code>reference</code> volta no <code>data</code>.</p>
    <pre><code>{example}</code></pre>
    <p>Cabeçalhos: <code>X-Paysi-Event-Id</code>, <code>X-Paysi-Timestamp</code> e <code>X-Paysi-Signature</code>.</p>

    <h2>4. Confira a assinatura</h2>
    <p>A assinatura é um HMAC-SHA256 de <code>timestamp + &quot;.&quot; + corpo</code> com o seu segredo. Recuse o envio se ela não bater. Depois de gerar um novo segredo, o anterior continua valendo por 24 horas (o cabeçalho <code>X-Paysi-Signature-Previous</code> acompanha o envio).</p>
    <pre><code>{verify}</code></pre>

    <h2>5. Responda rápido e com 2xx</h2>
    <p>Responda com qualquer status 2xx em até 10 segundos. Se falhar, a Paysi tenta de novo depois de 1 minuto, 5 minutos, 30 minutos, 2 horas e 12 horas. Na tela de logs do webhook você vê cada envio, a requisição e a resposta, e pode reenviar manualmente.</p>
    <p>Como o mesmo evento pode chegar mais de uma vez, use o <code>eventId</code> para ignorar repetições.</p>
  </div>;
}
