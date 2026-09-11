import { apiRequest, CursorPage } from "./api";

export type Bucket = "GUARANTEE" | "PENDING" | "RESERVE" | "AVAILABLE" | "DEBT" | "SYSTEM";
export type Direction = "CREDIT" | "DEBIT";

export type BalanceView = {
  guarantee: number;
  pending: number;
  reserve: number;
  available: number;
  debt: number;
  asOf: string;
};

export type KycStatus = "PENDING" | "SUBMITTED" | "APPROVED" | "REJECTED";

export type KycRequirement = {
  code: string;
  label: string;
  status: string;
  reason: string | null;
  estimatedAt: string | null;
};

export type KycView = {
  accountId: string;
  kycStatus: KycStatus;
  providerUrl: string | null;
  requirements: KycRequirement[];
};

export type LedgerItem = {
  entryId: number;
  bucket: Bucket;
  direction: Direction;
  amountCents: number;
  origin: string;
  reason: string | null;
  reference: string | null;
  availableAt: string | null;
  createdAt: string;
};

export function getBalance() {
  return apiRequest<BalanceView>("/v1/accounts/me/balance");
}

export function getKyc() {
  return apiRequest<KycView>("/v1/accounts/me");
}

export function getLedgerEntries(cursor?: string, limit = 20) {
  const query = new URLSearchParams({ limit: String(limit) });
  if (cursor) query.set("cursor", cursor);
  return apiRequest<CursorPage<LedgerItem>>(`/v1/accounts/me/ledger?${query}`);
}

export type DashboardAlert = {
  id: string;
  tone: "warning" | "danger";
  title: string;
  description: string;
  actionUrl: string | null;
};

export type DashboardPeriodPreset = "today" | "7d" | "30d";

export type DashboardBlock<T> = {
  state: "SUCCESS" | "EMPTY" | "ERROR";
  data?: T;
  code?: string;
  message?: string;
};

export type SalesSummary = { amountCents: number; count: number };
export type UpcomingReceivable = { amountCents: number; availableAt: string };
export type SubscriptionSummary = { active: number; pastDue: number };

export type RecentSale = {
  id: string;
  buyer: string;
  amountCents: number;
  method: string;
  status: string;
  occurredAt: string;
};

export type DashboardView = {
  period: { preset: DashboardPeriodPreset; from: string; to: string };
  salesToday: DashboardBlock<SalesSummary>;
  balance: DashboardBlock<BalanceView>;
  nextReceivables: DashboardBlock<UpcomingReceivable[]>;
  subscriptions: DashboardBlock<SubscriptionSummary>;
  alerts: DashboardBlock<DashboardAlert[]>;
  recentSales: DashboardBlock<RecentSale[]>;
};

export function getDashboard(period: DashboardPeriodPreset = "today") {
  return apiRequest<DashboardView>(`/v1/accounts/me/dashboard?period=${encodeURIComponent(period)}`);
}
