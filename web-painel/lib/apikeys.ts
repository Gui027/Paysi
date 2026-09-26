import { apiRequest } from "./api";

export type ApiScope = "reports" | "products" | "sales" | "sales_refund" | "affiliates" | "finance" | "webhooks";
export type ApiKey = { id: string; name: string; keyHint: string; scopes: ApiScope[]; createdAt: string; lastUsedAt: string | null; clientId: string; accountId: string };
export type CreatedApiKey = { key: ApiKey; clientSecret: string };

/** Endpoints que a chave pode acessar; "Reembolsar vendas" só vale junto com "Vendas". */
export const scopeOptions: readonly { scope: ApiScope; label: string; parent?: ApiScope }[] = [
  { scope: "reports", label: "Relatórios" },
  { scope: "products", label: "Produtos" },
  { scope: "sales", label: "Vendas" },
  { scope: "sales_refund", label: "Reembolsar vendas", parent: "sales" },
  { scope: "affiliates", label: "Afiliados" },
  { scope: "finance", label: "Financeiro" },
  { scope: "webhooks", label: "Webhooks" },
];
export const allScopes: ApiScope[] = scopeOptions.map(option => option.scope);

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
