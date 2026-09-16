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
 * Catálogo de eventos sugeridos — o backend aceita qualquer identificador que bata com o
 * padrão MAIUSCULO_COM_PONTOS (ver WebhookEndpointService), não existe uma lista fechada ainda.
 */
export const SUGGESTED_WEBHOOK_EVENTS = [
  "ORDER_PAID",
  "ORDER_REFUNDED",
  "CHARGE_FAILED",
  "SUBSCRIPTION_CANCELED",
  "SUBSCRIPTION_PAST_DUE",
  "PAYOUT_COMPLETED",
] as const;

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
