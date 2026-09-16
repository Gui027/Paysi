import { apiRequest, CursorPage } from "./api";

export type SubscriptionStatus =
  | "TRIALING"
  | "ACTIVE"
  | "PAST_DUE"
  | "CANCELED"
  | "EXPIRED";

export type SubscriptionMethod = "CARD" | "BOLETO";

export type SubscriptionSummary = {
  id: string;
  orderId: string;
  buyerNameMasked: string;
  productName: string;
  productId: string;
  offerId: string;
  offerTitle: string;
  status: SubscriptionStatus;
  method: SubscriptionMethod;
  priceCents: number;
  cycle: "MONTHLY" | "YEARLY";
  trialEnd: string | null;
  nextChargeAt: string | null;
  nextRetryAt: string | null;
  cancelAtPeriodEnd: boolean;
  attemptCount: number;
  createdAt: string;
};

export type ChargeRecord = {
  id: string;
  cycleNumber: number;
  status: "APPROVED" | "DECLINED" | "PENDING" | "REFUNDED";
  method: SubscriptionMethod;
  amountCents: number;
  occurredAt: string | null;
};

export type SubscriptionDetail = SubscriptionSummary & {
  charges: ChargeRecord[];
  guaranteeDays: number;
  affiliateName: string | null;
};

export type SubscriptionFilters = {
  query: string;
  status: "" | SubscriptionStatus;
  method: "" | SubscriptionMethod;
  productId: string;
};

export const subscriptionStatusLabel: Record<SubscriptionStatus, string> = {
  TRIALING: "Período de teste",
  ACTIVE: "Ativa",
  PAST_DUE: "Inadimplente",
  CANCELED: "Cancelada",
  EXPIRED: "Expirada",
};

export const subscriptionMethodLabel: Record<SubscriptionMethod, string> = {
  CARD: "Cartão de crédito",
  BOLETO: "Boleto",
};

export const cycleLabel: Record<"MONTHLY" | "YEARLY", string> = {
  MONTHLY: "Mensal",
  YEARLY: "Anual",
};

/** Rótulo textual para cada tentativa de cobrança da régua D+1/3/7/14 */
export function dunningDayLabel(attemptCount: number): string {
  const days = [1, 3, 7, 14];
  const next = days[attemptCount];
  if (next === undefined) return "Encerrado";
  return `D+${next}`;
}

export function subscriptionMatchesFilters(
  sub: SubscriptionSummary,
  filters: SubscriptionFilters,
): boolean {
  const q = filters.query.trim().toLowerCase();
  const matchesQuery =
    !q ||
    sub.id.toLowerCase().includes(q) ||
    sub.productName.toLowerCase().includes(q) ||
    sub.buyerNameMasked.toLowerCase().includes(q);

  const matchesStatus = !filters.status || sub.status === filters.status;
  const matchesMethod = !filters.method || sub.method === filters.method;
  const matchesProduct =
    !filters.productId || sub.productId === filters.productId;

  return matchesQuery && matchesStatus && matchesMethod && matchesProduct;
}

export function listSubscriptions(
  filters?: Partial<SubscriptionFilters>,
  cursor?: string,
  limit = 20,
) {
  const query = new URLSearchParams({ limit: String(limit) });
  if (cursor) query.set("cursor", cursor);
  if (filters?.status) query.set("status", filters.status);
  if (filters?.method) query.set("method", filters.method);
  if (filters?.productId) query.set("productId", filters.productId);
  if (filters?.query) query.set("query", filters.query);

  return apiRequest<CursorPage<SubscriptionSummary>>(
    `/v1/subscriptions?${query}`,
  );
}

export function getSubscription(subscriptionId: string) {
  return apiRequest<SubscriptionDetail>(
    `/v1/subscriptions/${encodeURIComponent(subscriptionId)}`,
  );
}

export function cancelSubscription(subscriptionId: string) {
  return apiRequest<void>(
    `/v1/subscriptions/${encodeURIComponent(subscriptionId)}/cancel`,
    { method: "POST" },
  );
}
