import { apiRequest } from "./api";

export type ApiScope = "payments_read" | "payments_write" | "subscriptions_read" | "subscriptions_write" | "reports" | "products" | "products_write" | "sales" | "sales_refund" | "affiliates" | "finance" | "webhooks";
export type ApiKey = { id: string; name: string; keyHint: string; scopes: ApiScope[]; createdAt: string; lastUsedAt: string | null; clientId: string; accountId: string };
export type CreatedApiKey = { key: ApiKey; apiKey: string };

/** Endpoints que a chave pode acessar; "Reembolsar vendas" só vale junto com "Vendas". */
export const scopeOptions: readonly { scope: ApiScope; label: string; description: string; group: string; parent?: ApiScope }[] = [
  { scope: "payments_read", label: "Ler pagamentos", description: "Consultar status, QR Code Pix e boleto.", group: "Pagamentos" },
  { scope: "payments_write", label: "Criar pagamentos", description: "Criar cobranças Pix, boleto e cartão.", group: "Pagamentos", parent: "payments_read" },
  { scope: "subscriptions_read", label: "Ler assinaturas", description: "Listar assinaturas, ciclos e cobranças.", group: "Assinaturas" },
  { scope: "subscriptions_write", label: "Gerenciar assinaturas", description: "Criar e cancelar assinaturas recorrentes.", group: "Assinaturas", parent: "subscriptions_read" },
  { scope: "sales", label: "Ler vendas", description: "Listar e consultar vendas da conta.", group: "Transações" },
  { scope: "sales_refund", label: "Estornar vendas", description: "Emitir reembolsos totais ou parciais.", group: "Transações", parent: "sales" },
  { scope: "finance", label: "Ler financeiro", description: "Consultar saldo e saques.", group: "Financeiro" },
  { scope: "products", label: "Ler produtos", description: "Listar e consultar produtos.", group: "Catálogo" },
  { scope: "products_write", label: "Gerenciar catálogo", description: "Criar, editar, publicar e arquivar produtos e ofertas.", group: "Catálogo", parent: "products" },
  { scope: "affiliates", label: "Ler afiliados", description: "Listar afiliados dos seus produtos.", group: "Catálogo" },
  { scope: "reports", label: "Ler relatórios", description: "Consultar relatórios operacionais.", group: "Relatórios" },
  { scope: "webhooks", label: "Gerenciar webhooks", description: "Criar, editar e listar endpoints.", group: "Webhooks" },
];
export const allScopes: ApiScope[] = scopeOptions.map(option => option.scope);
export const scopeGroups = [...new Set(scopeOptions.map(option => option.group))];

/** Marca ou desmarca um escopo respeitando a dependência: sem Vendas, não há Reembolsar vendas. */
export function toggleScope(current: ApiScope[], scope: ApiScope, on: boolean): ApiScope[] {
  let next = on ? [...new Set([...current, scope])] : current.filter(item => item !== scope);
  const option = scopeOptions.find(item => item.scope === scope);
  if (on && option?.parent && !next.includes(option.parent)) next = [...next, option.parent];
  if (!on) next = next.filter(item => scopeOptions.find(candidate => candidate.scope === item)?.parent !== scope);
  return next;
}

export function listApiKeys(q: string) {
  const params = new URLSearchParams();
  if (q.trim()) params.set("q", q.trim());
  return apiRequest<ApiKey[]>(`/v1/api-keys?${params}`);
}

export function getApiKey(id: string) {
  return apiRequest<ApiKey>(`/v1/api-keys/${id}`);
}

export function createApiKey(name: string, scopes: ApiScope[]) {
  return apiRequest<CreatedApiKey>("/v1/api-keys", { method: "POST", body: JSON.stringify({ name: name.trim(), scopes }) });
}

export function updateApiKey(id: string, name: string, scopes: ApiScope[]) {
  return apiRequest<ApiKey>(`/v1/api-keys/${id}`, { method: "PUT", body: JSON.stringify({ name: name.trim(), scopes }) });
}

export function deleteApiKey(id: string) {
  return apiRequest<void>(`/v1/api-keys/${id}`, { method: "DELETE" });
}
