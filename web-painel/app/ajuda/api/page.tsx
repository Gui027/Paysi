import Link from "next/link";
import type { ReactNode } from "react";

export const metadata = { title: "Referência da API | Paysi", description: "Documentação oficial da API Paysi para pagamentos, assinaturas e integrações." };

const BASE = "https://api.paysi.com.br";

function Code({ children }: { children: string }) {
  return <pre className="api-doc-code" tabIndex={0}><code>{children}</code></pre>;
}

function Endpoint({ method, path, title, scope, children }: { method: "GET" | "POST" | "PUT"; path: string; title: string; scope: string; children: ReactNode }) {
  return <article className="api-endpoint">
    <header><span className={`api-method api-method-${method.toLowerCase()}`}>{method}</span><code>{path}</code></header>
    <h3>{title}</h3><p className="api-scope-pill">Permissão: <code>{scope}</code></p>{children}
  </article>;
}

const nav = [
  ["Começar", [["Introdução", "introduction"], ["Autenticação", "authentication"], ["Primeiro pagamento", "quickstart"]]],
  ["Pagamentos", [["Criar pagamento", "create-payment"], ["Consultar pagamento", "get-payment"], ["Métodos e status", "payment-status"]]],
  ["Assinaturas", [["Criar assinatura", "create-subscription"], ["Consultar e listar", "get-subscription"], ["Cancelar", "cancel-subscription"]]],
  ["Catálogo", [["Criar produto", "create-product"], ["Criar e publicar oferta", "create-offer"], ["Gerenciar catálogo", "catalog-operations"]]],
  ["Operações", [["Vendas", "sales"], ["Reembolsos", "refunds"], ["Outros recursos", "other-resources"], ["Webhooks", "webhooks"]]],
  ["Referência", [["Idempotência", "idempotency"], ["Erros", "errors"], ["Limites e segurança", "security"]]],
] as const;

export default function Page() {
  return <div className="api-reference-shell">
    <aside className="api-doc-sidebar">
      <Link href="/ajuda" className="api-doc-brand">← Central de Ajuda</Link>
      <strong>Referência da API</strong>
      {nav.map(([group, links]) => <section key={group}><h2>{group}</h2>{links.map(([label, id]) => <a key={id} href={`#${id}`}>{label}</a>)}</section>)}
      <a className="api-spec-link" href="/paysi-openapi.json" download>Baixar OpenAPI JSON</a>
    </aside>

    <div className="api-doc-content">
      <section id="introduction" className="api-doc-hero">
        <span className="api-doc-version">API v1</span>
        <h1>API da Paysi</h1>
        <p>Integre Pix, boleto, cartão e assinaturas recorrentes à sua plataforma. A API usa JSON, HTTPS, valores inteiros em centavos e respostas previsíveis para facilitar integrações server-to-server.</p>
        <div className="api-base-url"><span>URL base</span><code>{BASE}/v1/public</code></div>
      </section>

      <section id="authentication" className="api-doc-section">
        <h2>Autenticação</h2>
        <p>Crie uma chave em <strong>Apps → API</strong>, selecione somente as permissões necessárias e guarde a chave exibida uma única vez. Envie-a em todas as chamadas pelo cabeçalho Bearer.</p>
        <Code>{`Authorization: Bearer paysi_live_SUA_CHAVE`}</Code>
        <div className="api-callout"><strong>Proteja sua chave.</strong> Use apenas no backend. Nunca publique em JavaScript do navegador, aplicativos móveis, repositórios ou logs.</div>
        <details><summary>Fluxo opcional com token de 1 hora</summary><p>Integrações que exigem credenciais temporárias podem trocar <code>client_id</code> e a chave por um token:</p><Code>{`curl -X POST ${BASE}/v1/public/oauth/token \\
  -H "Content-Type: application/json" \\
  -d '{"client_id":"SEU_CLIENT_ID","client_secret":"SUA_CHAVE"}'`}</Code></details>
      </section>

      <section id="quickstart" className="api-doc-section">
        <h2>Primeiro pagamento em menos de 5 minutos</h2>
        <ol className="api-steps"><li>Crie e publique um produto e uma oferta pelo painel ou pela API com <code>products_write</code>.</li><li>Crie uma chave com <code>payments_read</code> e <code>payments_write</code>.</li><li>Envie a cobrança Pix abaixo usando o slug da oferta.</li><li>Mostre o <code>pixQrCode</code> ao comprador e confirme o resultado por webhook ou consulta.</li></ol>
        <Code>{`curl -X POST ${BASE}/v1/public/payments \\
  -H "Authorization: Bearer paysi_live_SUA_CHAVE" \\
  -H "Idempotency-Key: pedido-8472" \\
  -H "Content-Type: application/json" \\
  -d '{
    "offerSlug": "curso-completo",
    "method": "PIX",
    "buyer": {
      "name": "Ana Silva",
      "email": "ana@exemplo.com",
      "personType": "PF",
      "taxId": "12345678909",
      "phone": "11999999999"
    },
    "termsHash": "termos-v1-2026",
    "termsAcceptedAt": "2026-11-10T15:00:00Z",
    "customerIp": "203.0.113.42",
    "reference": "pedido-8472"
  }'`}</Code>
      </section>

      <section id="create-payment" className="api-doc-section">
        <h2>Pagamentos</h2>
        <Endpoint method="POST" path="/payments" title="Criar e iniciar um pagamento" scope="payments_write">
          <p>Cria o pedido e inicia a cobrança em uma única chamada. O preço e as taxas vêm da oferta publicada; valores enviados pelo cliente nunca substituem o catálogo.</p>
          <h4>Cabeçalhos</h4><table><tbody><tr><th>Authorization</th><td>Obrigatório. Chave Bearer.</td></tr><tr><th>Idempotency-Key</th><td>Obrigatório. Identificador único da tentativa, até 24 horas.</td></tr></tbody></table>
          <h4>Campos principais</h4><table><thead><tr><th>Campo</th><th>Tipo</th><th>Descrição</th></tr></thead><tbody>
            <tr><td><code>offerSlug</code></td><td>string</td><td>Slug de uma oferta publicada da sua conta.</td></tr>
            <tr><td><code>method</code></td><td>enum</td><td><code>PIX</code>, <code>BOLETO</code> ou <code>CARD</code>.</td></tr>
            <tr><td><code>buyer</code></td><td>object</td><td>Nome, e-mail, tipo, CPF/CNPJ, telefone e endereço quando exigido.</td></tr>
            <tr><td><code>reference</code></td><td>string</td><td>ID do pedido no seu sistema. Recomendado.</td></tr>
            <tr><td><code>customerIp</code></td><td>string</td><td>IP do comprador final para evidência antifraude. Não envie o IP do seu servidor.</td></tr>
            <tr><td><code>amountCents</code></td><td>integer</td><td>Somente para ofertas de valor livre; mínimo definido pela oferta.</td></tr>
            <tr><td><code>card</code></td><td>object</td><td>Obrigatório para cartão. Nunca é armazenado pela Paysi.</td></tr>
          </tbody></table>
          <h4>Resposta Pix — 201</h4><Code>{`{
  "order": { "orderId": "5db...", "status": "PENDING", "paidCents": 4990, "method": "PIX" },
  "payment": {
    "chargeId": "91c...", "method": "PIX", "status": "PENDING",
    "pixQrCode": "00020126...", "expiresAt": "2026-11-10T15:30:00Z"
  },
  "nextAction": "display_pix_qr_code"
}`}</Code>
        </Endpoint>
      </section>

      <section id="get-payment" className="api-doc-section">
        <Endpoint method="GET" path="/payments/{paymentId}" title="Consultar pagamento" scope="payments_read">
          <p>Retorna apenas pagamentos pertencentes à conta da chave. Útil como fallback; para confirmação em tempo real, prefira webhooks.</p>
          <Code>{`curl ${BASE}/v1/public/payments/91c... \\
  -H "Authorization: Bearer paysi_live_SUA_CHAVE"`}</Code>
        </Endpoint>
      </section>

      <section id="payment-status" className="api-doc-section">
        <h2>Métodos, ações e status</h2>
        <table><thead><tr><th>Método</th><th>Próxima ação</th><th>Confirmação</th></tr></thead><tbody><tr><td>Pix</td><td>Exibir <code>pixQrCode</code></td><td>Webhook ou consulta</td></tr><tr><td>Boleto</td><td>Exibir <code>boletoBarcode</code> ou abrir <code>boletoUrl</code></td><td>Webhook</td></tr><tr><td>Cartão</td><td>Concluir 3DS quando <code>threeDs.required</code></td><td>Resposta e webhook</td></tr></tbody></table>
        <p>Status comuns: <code>PENDING</code>, <code>PAID</code>, <code>FAILED</code>, <code>EXPIRED</code>, <code>REFUNDED</code> e <code>PARTIALLY_REFUNDED</code>.</p>
      </section>

      <section id="create-subscription" className="api-doc-section">
        <h2>Assinaturas recorrentes</h2>
        <p>Crie uma oferta do tipo assinatura pelo painel ou pela API e use o slug para iniciar a recorrência. O cartão é tokenizado durante a chamada e não é persistido pela Paysi. A chave precisa das permissões <code>subscriptions_read</code> e <code>subscriptions_write</code>.</p>
        <Endpoint method="POST" path="/subscriptions" title="Criar assinatura" scope="subscriptions_write">
          <p>Exige <code>Idempotency-Key</code>. A mesma chave e o mesmo corpo retornam a assinatura original com HTTP 200, sem tokenizar ou cobrar o cartão novamente.</p>
          <Code>{`curl -X POST ${BASE}/v1/public/subscriptions \\
  -H "Authorization: Bearer paysi_live_SUA_CHAVE" \\
  -H "Idempotency-Key: assinatura-cliente-8472" \\
  -H "Content-Type: application/json" \\
  -d '{
    "offerSlug": "plano-pro-mensal",
    "method": "CARD",
    "buyer": {
      "name": "Ana Silva",
      "email": "ana@exemplo.com",
      "personType": "PF",
      "taxId": "12345678909"
    },
    "card": {
      "holderName": "Ana Silva",
      "number": "4242424242424242",
      "expiryMonth": "12",
      "expiryYear": "2030",
      "ccv": "123",
      "postalCode": "01310100",
      "addressNumber": "100",
      "phone": "11999999999"
    },
    "customerIp": "203.0.113.42",
    "visitorKey": "cliente-8472"
  }'`}</Code>
          <div className="api-callout"><strong>Dados de cartão.</strong> Envie esta chamada somente do seu backend, nunca registre o corpo e mantenha seu ambiente compatível com as obrigações de PCI DSS.</div>
          <h4>Resposta — 201</h4><Code>{`{
  "subscription": {
    "id": "6c8...",
    "status": "ACTIVE",
    "cycleNumber": 1,
    "nextChargeAt": "2026-12-10T15:00:00Z",
    "cancelPending": false,
    "hasPaymentMethod": true
  },
  "charges": [{ "cycleNumber": 1, "amountCents": 4990, "status": "PAID" }]
}`}</Code>
        </Endpoint>
      </section>

      <section id="get-subscription" className="api-doc-section">
        <Endpoint method="GET" path="/subscriptions/{subscriptionId}" title="Consultar assinatura" scope="subscriptions_read">
          <p>Retorna estado, próximo vencimento, cancelamento pendente e histórico de cobranças. Recursos de outra conta respondem como não encontrados.</p>
        </Endpoint>
        <Endpoint method="GET" path="/subscriptions" title="Listar assinaturas" scope="subscriptions_read">
          <p>Use <code>cursor</code> e <code>limit</code> (máximo 100). A resposta inclui <code>items</code> e <code>nextCursor</code>.</p>
        </Endpoint>
      </section>

      <section id="cancel-subscription" className="api-doc-section">
        <Endpoint method="POST" path="/subscriptions/{subscriptionId}/cancel" title="Cancelar ao fim do período" scope="subscriptions_write">
          <p>Agenda o cancelamento para o fim do ciclo vigente e responde HTTP 204. Repetir a solicitação é seguro e não antecipa o encerramento.</p>
          <Code>{`curl -X POST ${BASE}/v1/public/subscriptions/6c8.../cancel \\
  -H "Authorization: Bearer paysi_live_SUA_CHAVE"`}</Code>
        </Endpoint>
        <p>Status possíveis: <code>TRIAL</code>, <code>ACTIVE</code>, <code>PAST_DUE</code> e <code>CANCELED</code>. Renovações e falhas seguem as retentativas automáticas da Paysi.</p>
      </section>

      <section id="sales" className="api-doc-section"><h2>Vendas</h2>
        <Endpoint method="GET" path="/sales" title="Listar vendas" scope="sales"><p>Filtros: <code>status</code>, <code>method</code>, <code>productId</code>, <code>from</code>, <code>to</code>, <code>page</code> e <code>size</code>.</p></Endpoint>
        <Endpoint method="GET" path="/sales/{chargeId}" title="Detalhar venda" scope="sales"><p>Retorna comprador, valores, divisão financeira, método e histórico do pagamento.</p></Endpoint>
      </section>

      <section id="refunds" className="api-doc-section"><h2>Reembolsos</h2>
        <Endpoint method="POST" path="/sales/{chargeId}/refund" title="Reembolsar uma venda" scope="sales_refund"><p>Omita <code>amountCents</code> para reembolso total ou informe o valor para reembolso parcial. Exige <code>Idempotency-Key</code>.</p><Code>{`{
  "amountCents": 1500,
  "reason": "Solicitação do cliente"
}`}</Code></Endpoint>
      </section>

      <section id="create-product" className="api-doc-section"><h2>Catálogo</h2>
        <p>O escopo <code>products</code> permite consultar o catálogo. Para criar, editar, publicar ou arquivar produtos e ofertas, conceda também <code>products_write</code>.</p>
        <Endpoint method="POST" path="/products" title="Criar produto em rascunho" scope="products_write">
          <p>Cria um produto da conta autenticada. O tipo de cobrança e o segmento não podem mudar depois que o produto tiver ofertas.</p>
          <Code>{`curl -X POST ${BASE}/v1/public/products \
  -H "Authorization: Bearer paysi_live_SUA_CHAVE" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "CRM Profissional",
    "description": "Assinatura mensal do CRM",
    "segment": "SAAS",
    "chargeType": "SUBSCRIPTION",
    "affiliationEnabled": false
  }'`}</Code>
        </Endpoint>
      </section>

      <section id="create-offer" className="api-doc-section">
        <Endpoint method="POST" path="/products/{productId}/offers" title="Criar oferta em rascunho" scope="products_write">
          <p>Cria a oferta e gera seu <code>slug</code>. Para assinatura mensal, use <code>cycle: MONTHLY</code> e inclua <code>CARD</code> em <code>paymentMethods</code>.</p>
          <Code>{`curl -X POST ${BASE}/v1/public/products/SEU_PRODUCT_ID/offers \
  -H "Authorization: Bearer paysi_live_SUA_CHAVE" \
  -H "Content-Type: application/json" \
  -d '{
    "priceCents": 4990,
    "cycle": "MONTHLY",
    "trialDays": 0,
    "trialRequiresCard": false,
    "guaranteeDays": 7,
    "maxInstallments": 1,
    "boletoDueDays": 3,
    "boletoAdvanceDays": 5,
    "paymentMethods": ["CARD"],
    "payoutDelay": "D15",
    "name": "Plano Profissional mensal"
  }'`}</Code>
        </Endpoint>
        <Endpoint method="POST" path="/offers/{offerId}/publish" title="Publicar oferta" scope="products_write">
          <p>Publica a oferta quando a conta atende aos requisitos. Se KYC ou configuração fiscal estiverem pendentes, responde HTTP 202 com <code>requiredAction</code> e <code>actionUrl</code>.</p>
        </Endpoint>
      </section>

      <section id="catalog-operations" className="api-doc-section"><h2>Gerenciar catálogo</h2>
        <table><thead><tr><th>Método e rota</th><th>Permissão</th><th>Descrição</th></tr></thead><tbody>
          <tr><td><code>GET /products</code></td><td><code>products</code></td><td>Lista produtos com <code>cursor</code> e <code>limit</code>.</td></tr>
          <tr><td><code>{"GET /products/{productId}"}</code></td><td><code>products</code></td><td>Detalha um produto da conta.</td></tr>
          <tr><td><code>{"PUT /products/{productId}"}</code></td><td><code>products_write</code></td><td>Edita um produto.</td></tr>
          <tr><td><code>{"DELETE /products/{productId}"}</code></td><td><code>products_write</code></td><td>Arquiva um produto.</td></tr>
          <tr><td><code>{"GET /products/{productId}/offers"}</code></td><td><code>products</code></td><td>Lista as ofertas do produto.</td></tr>
          <tr><td><code>{"GET /offers/{offerId}"}</code></td><td><code>products</code></td><td>Detalha uma oferta.</td></tr>
          <tr><td><code>{"PUT /offers/{offerId}"}</code></td><td><code>products_write</code></td><td>Edita uma oferta.</td></tr>
          <tr><td><code>{"DELETE /offers/{offerId}"}</code></td><td><code>products_write</code></td><td>Arquiva uma oferta.</td></tr>
        </tbody></table>
      </section>

      <section id="other-resources" className="api-doc-section"><h2>Afiliados, relatórios e financeiro</h2>
        <table><thead><tr><th>Método e rota</th><th>Permissão</th><th>Descrição</th></tr></thead><tbody>
          <tr><td><code>GET /affiliates</code></td><td><code>affiliates</code></td><td>Lista afiliados dos seus produtos.</td></tr>
          <tr><td><code>GET /balance</code></td><td><code>finance</code></td><td>Consulta saldos disponível, pendente e bloqueado.</td></tr>
          <tr><td><code>GET /payouts</code></td><td><code>finance</code></td><td>Lista saques com paginação.</td></tr>
          <tr><td><code>{"GET /reports/{reportId}"}</code></td><td><code>reports</code></td><td>Consulta relatórios de produto, afiliado, abandonadas, recebíveis e assinaturas.</td></tr>
        </tbody></table>
      </section>

      <section id="webhooks" className="api-doc-section"><h2>Webhooks</h2>
        <p>Use <code>GET /webhooks</code>, <code>POST /webhooks</code> e <code>{"PUT /webhooks/{id}"}</code> com a permissão <code>webhooks</code>. O segredo de assinatura aparece somente na criação.</p>
        <Code>{`{
  "name": "Produção",
  "url": "https://sua-plataforma.com/webhooks/paysi",
  "events": ["PAYMENT.APPROVED", "PAYMENT.REFUSED", "SUBSCRIPTION.CREATED", "SUBSCRIPTION.RENEWED", "SUBSCRIPTION.PAST_DUE", "SUBSCRIPTION.CANCELED"],
  "enabled": true
}`}</Code>
        <p>Para assinaturas, acompanhe <code>SUBSCRIPTION.CREATED</code>, <code>SUBSCRIPTION.RENEWED</code>, <code>SUBSCRIPTION.PAST_DUE</code> e <code>SUBSCRIPTION.CANCELED</code>. A cobrança inicial aprovada também produz <code>PAYMENT.APPROVED</code>.</p>
        <div className="api-callout">Valide a assinatura antes de processar o evento, responda com HTTP 2xx rapidamente e trate eventos repetidos de forma idempotente.</div>
      </section>

      <section id="idempotency" className="api-doc-section"><h2>Idempotência</h2><p>Operações que movimentam dinheiro exigem <code>Idempotency-Key</code>. Repetir a mesma chave com o mesmo corpo devolve o resultado original; reutilizá-la com outro corpo retorna <code>409 IDEMPOTENCY_KEY_REUSED</code>. Use o ID estável da operação no seu sistema — não gere outro em cada retry.</p></section>

      <section id="errors" className="api-doc-section"><h2>Erros</h2><p>Erros usam JSON e incluem um código estável, uma mensagem legível e, quando aplicável, o campo inválido.</p><Code>{`{
  "code": "API_SCOPE_MISSING",
  "message": "Esta chave não tem a permissão necessária",
  "field": null,
  "correlationId": "01J..."
}`}</Code><table><thead><tr><th>HTTP</th><th>Significado</th></tr></thead><tbody><tr><td>400</td><td>Corpo ou regra de negócio inválida.</td></tr><tr><td>401</td><td>Chave ausente, inválida ou token expirado.</td></tr><tr><td>403</td><td>Permissão insuficiente ou conta incompatível.</td></tr><tr><td>404</td><td>Recurso inexistente ou de outra conta.</td></tr><tr><td>409</td><td>Conflito de estado ou idempotência.</td></tr><tr><td>429</td><td>Limite de requisições excedido; aguarde antes do retry.</td></tr><tr><td>5xx</td><td>Erro temporário; repita com backoff e a mesma chave de idempotência.</td></tr></tbody></table></section>

      <section id="security" className="api-doc-section"><h2>Limites e segurança</h2><ul><li>Use TLS 1.2 ou superior e nunca envie chaves por query string.</li><li>Crie uma chave por integração e aplique o menor conjunto de permissões.</li><li>Revogue imediatamente chaves expostas; a revogação também invalida tokens temporários.</li><li>Valores monetários são inteiros em centavos; datas usam ISO 8601 em UTC.</li><li>Paginação aceita no máximo 100 itens por página. Ao receber 429, aplique backoff exponencial.</li></ul></section>
    </div>
  </div>;
}
