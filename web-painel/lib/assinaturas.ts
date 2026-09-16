import { apiRequest, CursorPage } from "./api";

export type SubscriptionStatus = "TRIAL" | "ACTIVE" | "PAST_DUE" | "CANCELED";

export type ChargeStatus = "PENDING" | "PAID" | "FAILED" | "EXPIRED" | "PARTIALLY_REFUNDED" | "REFUNDED" | "CHARGEBACK";

export type Subscription = {
  id: string;
  orderId: string;
  offerId: string;
  status: SubscriptionStatus;
  cycleNumber: number;
  trialEndsAt: string | null;
  nextChargeAt: string | null;
  canceledAt: string | null;
  cancelPending: boolean;
  hasPaymentMethod: boolean;
  createdAt: string;
};

export type SubscriptionCharge = {
  id: string;
  cycleNumber: number;
  amountCents: number;
  status: ChargeStatus;
  attemptCount: number;
  nextRetryAt: string | null;
  paidAt: string | null;
  createdAt: string;
};

export type SubscriptionDetail = {
  subscription: Subscription;
  charges: SubscriptionCharge[];
};

export const subscriptionStatusLabel: Record<SubscriptionStatus, string> = {
  TRIAL: "Em teste grátis",
  ACTIVE: "Ativa",
  PAST_DUE: "Pagamento em atraso",
  CANCELED: "Cancelada",
};

export const chargeStatusLabel: Record<ChargeStatus, string> = {
  PENDING: "Aguardando",
  PAID: "Paga",
  FAILED: "Recusada",
  EXPIRED: "Expirada",
  PARTIALLY_REFUNDED: "Reembolso parcial",
  REFUNDED: "Reembolsada",
  CHARGEBACK: "Contestada",
};

/** Régua de retentativa fixa do backend (BE-10.2): D+1, D+3, D+7, D+14 a partir da 1ª falha. */
export const DUNNING_SCHEDULE_DAYS = [1, 3, 7, 14] as const;

export function listSubscriptions(cursor?: string, limit = 20) {
  const query = new URLSearchParams({ limit: String(limit) });
  if (cursor) query.set("cursor", cursor);
  return apiRequest<CursorPage<Subscription>>(`/v1/accounts/me/subscriptions?${query}`);
}

export function getSubscription(subscriptionId: string) {
  return apiRequest<SubscriptionDetail>(`/v1/subscriptions/${encodeURIComponent(subscriptionId)}`);
}

export function cancelSubscription(subscriptionId: string) {
  return apiRequest<void>(`/v1/subscriptions/${encodeURIComponent(subscriptionId)}/cancel`, {
    method: "POST",
  });
}

export function isTrialWithoutCard(subscription: Subscription): boolean {
  return subscription.status === "TRIAL" && !subscription.hasPaymentMethod;
}
