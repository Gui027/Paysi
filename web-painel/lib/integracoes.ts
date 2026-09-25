import { apiRequest } from "./api";

export type WebhookEndpoint = {
  id: string;
  url: string;
  events: string[];
  enabled: boolean;
  secretRotatedAt: string | null;
  createdAt: string;
};

export type CreatedWebhookEndpoint = {
  endpoint: WebhookEndpoint;
  secret: string;
};

export type RotatedWebhookSecret = {
  endpointId: string;
  secret: string;
  previousSecretValidUntil: string;
};

export type WebhookDelivery = {
  id: string;
  eventId: string;
  endpointId: string;
  attempt: number;
  httpStatus: number | null;
  error: string | null;
  nextRetryAt: string | null;
  createdAt: string;
};

/**
 * Eventos que a Paysi de fato envia. O backend compara o nome sem diferenciar maiúsculas de
 * minúsculas, então quem já cadastrou em minúsculas continua recebendo.
 */
export const WEBHOOK_EVENT_DESCRIPTIONS = {
  "PAYMENT.APPROVED": "Pagamento aprovado (venda nova ou renovação de assinatura)",
  "PAYMENT.REFUNDED": "Pagamento reembolsado por completo",
  "PAYMENT.PARTIALLY_REFUNDED": "Pagamento reembolsado em parte",
  "CHARGEBACK.OPENED": "Contestação (chargeback) aberta",
  "SUBSCRIPTION.PAST_DUE": "Assinatura em atraso (cobrança do ciclo falhou)",
  "SUBSCRIPTION.CANCELED": "Assinatura cancelada",
  "INVOICE.ISSUED": "Nota fiscal emitida",
} as const;

export const SUGGESTED_WEBHOOK_EVENTS = Object.keys(WEBHOOK_EVENT_DESCRIPTIONS) as (keyof typeof WEBHOOK_EVENT_DESCRIPTIONS)[];

export function listWebhookEndpoints() {
  return apiRequest<WebhookEndpoint[]>("/v1/accounts/me/webhooks");
}

export function createWebhookEndpoint(url: string, events: string[], enabled: boolean) {
  return apiRequest<CreatedWebhookEndpoint>("/v1/accounts/me/webhooks", {
    method: "POST",
    body: JSON.stringify({ url, events, enabled }),
  });
}

export function updateWebhookEndpoint(id: string, url: string, events: string[], enabled: boolean) {
  return apiRequest<WebhookEndpoint>(`/v1/accounts/me/webhooks/${encodeURIComponent(id)}`, {
    method: "PUT",
    body: JSON.stringify({ url, events, enabled }),
  });
}

export function rotateWebhookSecret(id: string) {
  return apiRequest<RotatedWebhookSecret>(`/v1/accounts/me/webhooks/${encodeURIComponent(id)}/rotate-secret`, {
    method: "POST",
  });
}

export function listWebhookDeliveries(limit = 50) {
  return apiRequest<WebhookDelivery[]>(`/v1/accounts/me/webhooks/deliveries?limit=${limit}`);
}

export function resendWebhookDelivery(eventId: string) {
  return apiRequest<void>(`/v1/accounts/me/webhooks/deliveries/${encodeURIComponent(eventId)}/resend`, {
    method: "POST",
  });
}

export function deliveryStatusLabel(delivery: WebhookDelivery): string {
  if (delivery.httpStatus && delivery.httpStatus >= 200 && delivery.httpStatus < 300) return "Entregue";
  if (delivery.nextRetryAt) return "Vai tentar de novo";
  return "Falhou";
}
