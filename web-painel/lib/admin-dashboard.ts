import { ApiRequestError } from "./api";

export type AdminSummary = {
  totalRevenueCents: number;
  periodRevenueCents: number;
  paidSales: number;
  activeSubscriptions: number;
  newAccounts: number;
};

export type RevenuePoint = { date: string; revenueCents: number; sales: number };
export type AdminAccount = {
  id: string;
  name: string;
  email: string;
  status: "ACTIVE" | "LIMITED" | "SUSPENDED" | "CLOSED";
  revenueCents: number;
  paidSales: number;
  createdAt: string;
};

export type AdminDashboard = {
  summary: AdminSummary;
  revenueTimeline: RevenuePoint[];
  accounts: AdminAccount[];
  page: number;
  size: number;
  totalAccounts: number;
  totalPages: number;
};

async function adminRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`/api${path}`, {
    ...init,
    credentials: "include",
    cache: "no-store",
    headers: {
      Accept: "application/json",
      ...(init.body ? { "Content-Type": "application/json" } : {}),
      ...init.headers,
    },
  });
  const contentType = response.headers.get("content-type") ?? "";
  const body = contentType.includes("application/json") ? await response.json() : null;
  if (!response.ok) throw new ApiRequestError(response.status, body ?? {});
  return body as T;
}

export function adminLogin(username: string, password: string) {
  return adminRequest<{ username: string }>("/v1/admin-dashboard/sessions", {
    method: "POST",
    body: JSON.stringify({ username, password }),
  });
}

export function adminLogout() {
  return adminRequest<void>("/v1/admin-dashboard/sessions/current", { method: "DELETE" });
}

export function getAdminDashboard(days: number, query: string, page: number) {
  const params = new URLSearchParams({ days: String(days), page: String(page), size: "20" });
  if (query.trim()) params.set("q", query.trim());
  return adminRequest<AdminDashboard>(`/v1/admin-dashboard?${params}`);
}
