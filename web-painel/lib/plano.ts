import { apiRequest } from "./api";

export type CommercialPlan = "TRANSACIONAL" | "ESCALA";
export type PlanStatus = "ACTIVE" | "PAST_DUE" | "DOWNGRADED";

export type PlanView = {
  currentPlan: CommercialPlan;
  monthlyFee: number;
  currentPeriodStart: string;
  nextBilling: string;
  status: PlanStatus;
  pastDueSince: string | null;
  pendingPlan: CommercialPlan | null;
  pendingMonthlyFee: number | null;
  pendingEffectiveAt: string | null;
  priceTable: Record<CommercialPlan, number>;
};

export type PlanChangeRecord = {
  id: string;
  fromPlan: string | null;
  toPlan: string;
  priceTable: string;
  createdAt: string;
};

export const planLabel: Record<CommercialPlan, string> = {
  TRANSACIONAL: "Transacional",
  ESCALA: "Escala",
};

export const planStatusLabel: Record<PlanStatus, string> = {
  ACTIVE: "Em dia",
  PAST_DUE: "Pagamento em atraso",
  DOWNGRADED: "Rebaixado por inadimplência",
};

export function getPlan() {
  return apiRequest<PlanView>("/v1/accounts/me/plan");
}

export function getPlanHistory(limit = 20) {
  return apiRequest<PlanChangeRecord[]>(`/v1/accounts/me/plan/history?limit=${limit}`);
}

export function requestPlanChange(plan: CommercialPlan, cardToken?: string) {
  return apiRequest<PlanView>("/v1/accounts/me/plan/change", {
    method: "POST",
    body: JSON.stringify({ plan, cardToken: cardToken || undefined }),
  });
}
