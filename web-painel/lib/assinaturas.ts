import { apiRequest } from "./api";
import { downloadCsv, SaleDetail, SaleMethod, SaleStatus } from "./vendas";

export type SubscriptionStatus = "TRIAL" | "ACTIVE" | "PAST_DUE" | "CANCELED";
export type SubscriptionCycle = "MONTHLY" | "QUARTERLY" | "SEMIANNUAL" | "ANNUAL";
export type SubscriptionsTab = "active" | "canceled" | "all";

export type SubscriptionRow = {
  id: string;
  code: string;
  createdAt: string;
  status: SubscriptionStatus;
  cancelPending: boolean;
  productName: string;
  productId: string;
  offerName: string | null;
  cycle: SubscriptionCycle;
  buyerName: string;
  buyerEmail: string;
  /** Líquido do vendedor na cobrança mais recente; nulo enquanto não houve cobrança (teste grátis). */
  netCents: number | null;
  nextChargeAt: string | null;
};

export type SubscriptionsPage = {
  items: SubscriptionRow[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
  summary: { activeCount: number; monthlyRecurringCents: number };
};

export type SubscriptionPayment = {
  chargeId: string;
  cycleNumber: number;
  createdAt: string;
  paidAt: string | null;
  status: SaleStatus;
  netCents: number;
};

export type SubscriptionDetail = {
  id: string;
  code: string;
  status: SubscriptionStatus;
  cancelPending: boolean;
  type: "PRODUCER";
  createdAt: string;
  accessUntil: string | null;
  trialEndsAt: string | null;
  nextChargeAt: string | null;
  canceledAt: string | null;
  productName: string;
  productId: string;
  offerName: string | null;
  cycle: SubscriptionCycle;
  netCents: number | null;
  installments: number;
  method: SaleMethod;
  approvedCharges: number;
  buyer: SaleDetail["buyer"];
  payments: SubscriptionPayment[];
  canCancel: boolean;
};

export type SubscriptionsQuery = {
  tab: SubscriptionsTab;
  q: string;
  statuses: SubscriptionStatus[];
  cycle: "" | SubscriptionCycle;
  method: "" | SaleMethod;
  productId: string;
  from: string;
  to: string;
  page: number;
};

export const emptySubscriptionsQuery: SubscriptionsQuery = { tab: "active", q: "", statuses: [], cycle: "", method: "", productId: "", from: "", to: "", page: 1 };

export const subscriptionStatusLabel: Record<SubscriptionStatus, string> = {
  ACTIVE: "Ativo",
  TRIAL: "Em teste",
  PAST_DUE: "Em atraso",
  CANCELED: "Cancelado",
};

export const cycleLabel: Record<SubscriptionCycle, string> = { MONTHLY: "Mensal", QUARTERLY: "Trimestral", SEMIANNUAL: "Semestral", ANNUAL: "Anual" };
export const cycleUnit: Record<SubscriptionCycle, string> = { MONTHLY: "mês", QUARTERLY: "trimestre", SEMIANNUAL: "semestre", ANNUAL: "ano" };

/** Texto do status na lista: um cancelamento agendado ainda está ativo, mas o vendedor precisa ver que vai acabar. */
export function statusText(subscription: { status: SubscriptionStatus; cancelPending: boolean }): string {
  return subscription.cancelPending ? "Cancelamento agendado" : subscriptionStatusLabel[subscription.status];
}

export function planName(subscription: { offerName: string | null; cycle: SubscriptionCycle }): string {
  return subscription.offerName?.trim() || `Plano ${cycleLabel[subscription.cycle]}`;
}

/** Só monta a query string; o faturamento recorrente e os líquidos vêm prontos do backend. */
export function subscriptionsParams(query: SubscriptionsQuery, paged = true): URLSearchParams {
  const params = new URLSearchParams({ tab: query.tab });
  if (query.q.trim()) params.set("q", query.q.trim());
  query.statuses.forEach(status => params.append("status", status));
  if (query.cycle) params.set("cycle", query.cycle);
  if (query.method) params.set("method", query.method);
  if (query.productId) params.set("productId", query.productId);
  if (query.from) params.set("from", query.from);
  if (query.to) params.set("to", query.to);
  if (paged) params.set("page", String(query.page));
  return params;
}

export function listSubscriptions(query: SubscriptionsQuery) {
  return apiRequest<SubscriptionsPage>(`/v1/subscriptions?${subscriptionsParams(query)}`);
}

export function getSubscription(subscriptionId: string) {
  return apiRequest<SubscriptionDetail>(`/v1/subscriptions/${encodeURIComponent(subscriptionId)}/details`);
}

/** O cancelamento vale ao fim do período já pago: o cliente mantém o acesso até lá. */
export function cancelSubscription(subscriptionId: string) {
  return apiRequest<void>(`/v1/subscriptions/${encodeURIComponent(subscriptionId)}/cancel`, { method: "POST" });
}

export function downloadSubscriptionsCsv(query: SubscriptionsQuery): Promise<Blob> {
  return downloadCsv(`/api/v1/subscriptions/export?${subscriptionsParams(query, false)}`);
}
