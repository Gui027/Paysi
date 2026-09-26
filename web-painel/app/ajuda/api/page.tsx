import Link from "next/link";

export const metadata = { title: "API da Paysi" };

const BASE = "https://api.paysi.com.br";

const endpoints: readonly [string, string, string, string][] = [
  ["GET", "/v1/public/me", "qualquer chave", "Confere o token, a conta e os endpoints liberados."],
  ["GET", "/v1/public/products", "products", "Lista os produtos (cursor e limit)."],
  ["GET", "/v1/public/products/{id}", "products", "Detalha um produto."],
  ["GET", "/v1/public/sales", "sales", "Lista vendas. Filtros: tab (all ou approved), q, status, method, productId, from, to, page, size."],
  ["GET", "/v1/public/sales/{id}", "sales", "Detalha uma venda, com cliente, valores e divisão."],
  ["POST", "/v1/public/sales/{id}/refund", "sales_refund", "Reembolsa a venda. Sem amountCents é reembolso total. Exige o cabeçalho Idempotency-Key."],
  ["GET", "/v1/public/affiliates", "affiliates", "Lista os afiliados dos seus produtos."],
  ["GET", "/v1/public/balance", "finance", "Saldos, chave Pix e regras de saque."],
  ["GET", "/v1/public/payouts", "finance", "Lista os saques (page e size)."],
  ["GET", "/v1/public/reports/{relatorio}", "reports", "Relatórios: produto, afiliado, abandonadas, saldo-receber, recebiveis-cartao e assinaturas-canceladas. Filtros: from, to, productId, q, tab, page."],
  ["GET", "/v1/public/webhooks", "webhooks", "Lista os endpoints de webhook."],
  ["POST", "/v1/public/webhooks", "webhooks", "Cria um webhook (name obrigatório; productId opcional restringe a um produto). O segredo de assinatura aparece só nesta resposta."],
  ["PUT", "/v1/public/webhooks/{id}", "webhooks", "Edita um webhook (name, productId, url, events e enabled). Veja os eventos e a assinatura em Webhooks."],
];

export default function Page() {
  return <div className="aj-wrap aj-doc">
    <nav aria-label="Você está em"><Link href="/ajuda">Central de Ajuda</Link> <span aria-hidden="true">›</span> <span>API</span></nav>
    <h1>API da Paysi</h1>
    <p>Use a API para consultar produtos, vendas, afiliados, saldo e relatórios, reembolsar vendas e gerenciar webhooks a partir do seu sistema. Todos os valores em dinheiro são inteiros em centavos.</p>

    <h2>1. Crie uma API Key</h2>
    <p>No painel, abra <strong>Apps → API → Criar API Key</strong>, escolha os endpoints que a chave pode acessar e copie o <code>client_secret</code>. Ele só aparece uma vez. O <code>client_id</code> e o <code>account_id</code> ficam disponíveis ao editar a chave.</p>

    <h2>2. Gere um token de acesso</h2>
    <p>Troque o <code>client_id</code> e o <code>client_secret</code> por um token que vale por 1 hora:</p>
    <pre><code>{`curl -X POST ${BASE}/v1/public/oauth/token \\
  -H "Content-Type: application/json" \\
  -d '{"client_id": "SEU_CLIENT_ID", "client_secret": "SEU_CLIENT_SECRET"}'`}</code></pre>
    <pre><code>{`{
  "access_token": "pay_...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "sales finance"
}`}</code></pre>

    <h2>3. Chame a API</h2>
    <p>Envie o token no cabeçalho <code>Authorization</code>. O cabeçalho <code>X-Paysi-Account-Id</code> é opcional; se enviado, precisa ser o <code>account_id</code> da chave.</p>
    <pre><code>{`curl "${BASE}/v1/public/sales?tab=approved&size=20" \\
  -H "Authorization: Bearer SEU_TOKEN" \\
  -H "X-Paysi-Account-Id: SEU_ACCOUNT_ID"`}</code></pre>
    <p>Para reembolsar, envie um <code>Idempotency-Key</code> novo por tentativa. Repetir a mesma chave não reembolsa duas vezes:</p>
    <pre><code>{`curl -X POST ${BASE}/v1/public/sales/ID_DA_VENDA/refund \\
  -H "Authorization: Bearer SEU_TOKEN" \\
  -H "Idempotency-Key: 5f0c2a1e-9d1b-4a52-8a3e-0f4c8b1d7a10" \\
  -H "Content-Type: application/json" \\
  -d '{"amountCents": 1500, "reason": "Pedido do cliente"}'`}</code></pre>

    <h2>Endpoints</h2>
    <table>
      <caption className="sr-only">Endpoints da API pública</caption>
      <thead><tr><th scope="col">Método</th><th scope="col">Caminho</th><th scope="col">Permissão</th><th scope="col">O que faz</th></tr></thead>
      <tbody>{endpoints.map(([method, path, scope, text]) => <tr key={`${method} ${path}`}>
        <td><code>{method}</code></td><td><code>{path}</code></td><td><code>{scope}</code></td><td>{text}</td>
      </tr>)}</tbody>
    </table>

    <h2>Erros</h2>
    <table>
      <caption className="sr-only">Códigos de erro</caption>
      <thead><tr><th scope="col">Status</th><th scope="col">Código</th><th scope="col">Quando</th></tr></thead>
      <tbody>
        <tr><td>401</td><td><code>API_INVALID_CLIENT</code></td><td>client_id ou client_secret inválido.</td></tr>
        <tr><td>401</td><td><code>API_UNAUTHENTICATED</code></td><td>Faltou o cabeçalho Authorization: Bearer.</td></tr>
        <tr><td>401</td><td><code>API_TOKEN_INVALID</code></td><td>Token inválido, expirado ou de uma chave excluída. Gere um novo.</td></tr>
        <tr><td>403</td><td><code>API_SCOPE_MISSING</code></td><td>A chave não tem permissão para o endpoint. Edite a chave no painel.</td></tr>
        <tr><td>403</td><td><code>API_ACCOUNT_MISMATCH</code></td><td>O X-Paysi-Account-Id não pertence à chave.</td></tr>
      </tbody>
    </table>

    <h2>Boas práticas</h2>
    <p>Guarde o client_secret apenas no servidor, nunca em aplicativos de celular ou no navegador. Se ele vazar, exclua a API Key no painel: os tokens dela deixam de valer na hora.</p>
  </div>;
}
