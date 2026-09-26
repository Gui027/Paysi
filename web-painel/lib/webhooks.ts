import { apiRequest, CursorPage } from "./api";
import { listProducts, Product } from "./produtos";

export type WebhookItem = {
  id: string; name: string | null; url: string; productId: string | null; productName: string | null;
  events: string[]; enabled: boolean; createdAt: string;
};
export type WebhookView = {
  id: string; url: string; events: string[]; enabled: boolean; secretRotatedAt: string | null; createdAt: string;
  name: string | null; productId: string | null;
};
export type CreatedWebhook = { endpoint: WebhookView; secret: string };
export type RotatedSecret = { endpointId: string; secret: string; previousSecretValidUntil: string };
export type WebhookTestResult = { success: boolean; statusCode: number | null; error: string | null; responseBody: string | null };

export type LogStatus = "SUCCESS" | "FAILED" | "RETRYING";
export type LogRow = { eventId: string; eventType: string; saleCode: string | null; sentAt: string; status: LogStatus; statusCode: number | null; attempts: number };
export type LogsPage = { items: LogRow[]; page: number; size: number; total: number; totalPages: number };
export type LogDetail = {
  eventId: string; eventType: string; url: string; sentAt: string; status: LogStatus; statusCode: number | null; error: string | null;
  requestBody: string | null; responseBody: string | null; attempts: number; canResend: boolean;
};

/** Eventos que a Paysi envia; "Reembolso" cobre o total e o parcial. */
export const eventCatalog: readonly { key: string; label: string; types: string[] }[] = [
  { key: "boleto", label: "Boleto gerado", types: ["BOLETO.GENERATED"] },
  { key: "pix", label: "Pix gerado", types: ["PIX.GENERATED"] },
  { key: "cart", label: "Carrinho abandonado", types: ["CART.ABANDONED"] },
  { key: "refused", label: "Compra recusada", types: ["PAYMENT.REFUSED"] },
  { key: "approved", label: "Compra aprovada", types: ["PAYMENT.APPROVED"] },
  { key: "refund", label: "Reembolso", types: ["PAYMENT.REFUNDED", "PAYMENT.PARTIALLY_REFUNDED"] },
  { key: "chargeback", label: "Chargeback", types: ["CHARGEBACK.OPENED"] },
  { key: "sub_canceled", label: "Assinatura cancelada", types: ["SUBSCRIPTION.CANCELED"] },
  { key: "sub_past_due", label: "Assinatura atrasada", types: ["SUBSCRIPTION.PAST_DUE"] },
  { key: "sub_renewed", label: "Assinatura renovada", types: ["SUBSCRIPTION.RENEWED"] },
  { key: "invoice", label: "Nota fiscal emitida", types: ["INVOICE.ISSUED"] },
];
export const allEventKeys = eventCatalog.map(item => item.key);

export function typesFromKeys(keys: string[]): string[] {
  return eventCatalog.filter(item => keys.includes(item.key)).flatMap(item => item.types);
}

export function keysFromTypes(types: string[]): string[] {
  const upper = types.map(type => type.toUpperCase());
  return eventCatalog.filter(item => item.types.some(type => upper.includes(type))).map(item => item.key);
}

export function eventLabel(type: string): string {
  const upper = type.toUpperCase();
  if (upper === "WEBHOOK.TEST") return "Teste";
  return eventCatalog.find(item => item.types.includes(upper))?.label ?? type;
}

export const logStatusLabel: Record<LogStatus, string> = { SUCCESS: "Sucesso", FAILED: "Falhou", RETRYING: "Tentando novamente" };

export function isValidWebhookUrl(value: string): boolean {
  try {
    const url = new URL(value.trim());
    return url.protocol === "https:" && url.hostname.includes(".") && !url.username && !url.hash;
  } catch {
    return false;
  }
}

/** Mostra JSON indentado; se o texto não for JSON (como uma resposta em HTML), devolve como veio. */
export function prettyBody(value: string | null): string {
  if (!value) return "";
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

export function listWebhooks(q: string, productId: string) {
  const params = new URLSearchParams();
  if (q.trim()) params.set("q", q.trim());
  if (productId) params.set("productId", productId);
  return apiRequest<WebhookItem[]>(`/v1/webhooks?${params}`);
}

export function getWebhook(id: string) {
  return apiRequest<WebhookView>(`/v1/webhooks/${id}`);
}

export type WebhookInput = { name: string; productId: string; url: string; events: string[] };

function body(input: WebhookInput) {
  return JSON.stringify({ name: input.name.trim(), productId: input.productId || null, url: input.url.trim(), events: input.events });
}

export function createWebhook(input: WebhookInput) {
  return apiRequest<CreatedWebhook>("/v1/webhooks", { method: "POST", body: body(input) });
}

export function updateWebhook(id: string, input: WebhookInput) {
  return apiRequest<WebhookView>(`/v1/webhooks/${id}`, { method: "PUT", body: body(input) });
}

export function deleteWebhook(id: string) {
  return apiRequest<void>(`/v1/webhooks/${id}`, { method: "DELETE" });
}

export function rotateWebhookSecret(id: string) {
  return apiRequest<RotatedSecret>(`/v1/webhooks/${id}/rotate-secret`, { method: "POST" });
}

export function testWebhook(url: string, endpointId: string | null) {
  return apiRequest<WebhookTestResult>("/v1/webhooks/test", { method: "POST", body: JSON.stringify({ url: url.trim(), endpointId }) });
}

export type LogsQuery = { q: string; event: string; from: string; to: string; page: number };
export const emptyLogsQuery: LogsQuery = { q: "", event: "", from: "", to: "", page: 1 };

export function listLogs(id: string, query: LogsQuery) {
  const params = new URLSearchParams({ page: String(query.page) });
  if (query.q.trim()) params.set("q", query.q.trim());
  for (const type of typesFromKeys(query.event ? [query.event] : [])) params.append("event", type);
  if (query.from) params.set("from", query.from);
  if (query.to) params.set("to", query.to);
  return apiRequest<LogsPage>(`/v1/webhooks/${id}/logs?${params}`);
}

export function getLog(id: string, eventId: string) {
  return apiRequest<LogDetail>(`/v1/webhooks/${id}/logs/${eventId}`);
}

export function resendLog(id: string, eventId: string) {
  return apiRequest<void>(`/v1/webhooks/${id}/logs/${eventId}/resend`, { method: "POST" });
}

export function resendLogs(id: string, eventIds: string[]) {
  return apiRequest<{ sent: number }>(`/v1/webhooks/${id}/logs/resend`, { method: "POST", body: JSON.stringify({ eventIds }) });
}

/** Todos os produtos do vendedor, seguindo o cursor (a lista da API vem em páginas de 20). */
export async function listAllProducts(): Promise<Product[]> {
  const all: Product[] = [];
  let cursor: string | undefined;
  for (let guard = 0; guard < 25; guard += 1) {
    const page: CursorPage<Product> = await listProducts(cursor);
    all.push(...page.items);
    if (!page.nextCursor) break;
    cursor = page.nextCursor;
  }
  return all;
}
