import { apiRequest, CursorPage } from "./api";

export type AffiliationRecurrence = "FIRST_CHARGE" | "ALL_CYCLES";
export type AffiliationStatus = "PENDING" | "APPROVED" | "ENDED" | "FRAUD_ENDED";
export type AffiliationEndReason = "BY_SELLER" | "BY_AFFILIATE" | "FRAUD";

export type Affiliation = {
  id: string;
  productId: string;
  product: string;
  affiliateId: string;
  affiliate: string;
  seller: string;
  commissionBps: number;
  recurrence: AffiliationRecurrence;
  status: AffiliationStatus;
  endedReason: AffiliationEndReason | null;
  approvedAt: string | null;
  endedAt: string | null;
  createdAt: string;
};

export type MarketplaceSegment = "SAAS" | "DIGITAL";
export type MarketplaceChargeType = "ONE_TIME" | "SUBSCRIPTION";

export type MarketplaceItem = {
  productId: string;
  product: string;
  description: string | null;
  seller: string;
  segment: MarketplaceSegment;
  chargeType: MarketplaceChargeType;
  startingPriceCents: number;
  suggestedCommissionBps: number | null;
  guaranteeDays: number;
  payoutDelayDays: number;
  attributionDays: number;
};

export const affiliationStatusLabel: Record<AffiliationStatus, string> = {
  PENDING: "Pendente",
  APPROVED: "Aprovada",
  ENDED: "Encerrada",
  FRAUD_ENDED: "Encerrada por fraude",
};

export const recurrenceLabel: Record<AffiliationRecurrence, string> = {
  FIRST_CHARGE: "Somente na primeira cobrança",
  ALL_CYCLES: "Em todos os ciclos",
};

export const endReasonLabel: Record<AffiliationEndReason, string> = {
  BY_SELLER: "Encerrada pelo vendedor",
  BY_AFFILIATE: "Encerrada pelo afiliado",
  FRAUD: "Fraude",
};

export function parseCommissionPercent(value: string): number | null {
  const normalized = value.trim().replace(",", ".");
  if (!/^\d{1,2}(?:\.\d{1,2})?$/.test(normalized)) return null;
  const [wholeText, decimalText = ""] = normalized.split(".");
  const bps = Number(wholeText) * 100 + Number(decimalText.padEnd(2, "0"));
  return bps <= 5000 ? bps : null;
}

export function formatCommissionBps(bps: number): string {
  const whole = Math.trunc(bps / 100);
  const decimal = String(bps % 100).padStart(2, "0").replace(/0+$/, "");
  return `${whole}${decimal ? `,${decimal}` : ""}%`;
}

export function listSellerAffiliations(cursor?: string) {
  const query = new URLSearchParams({ role: "SELLER", limit: "20" });
  if (cursor) query.set("cursor", cursor);
  return apiRequest<CursorPage<Affiliation>>(`/v1/affiliations?${query}`);
}

export function approveAffiliation(id: string, commissionBps: number, recurrence: AffiliationRecurrence) {
  return apiRequest<Affiliation>(`/v1/affiliations/${encodeURIComponent(id)}/approve`, {
    method: "POST",
    body: JSON.stringify({ commissionBps, recurrence }),
  });
}

export function endAffiliation(id: string, reason: "BY_SELLER" | "FRAUD") {
  return apiRequest<Affiliation>(`/v1/affiliations/${encodeURIComponent(id)}/end`, {
    method: "POST",
    body: JSON.stringify({ reason }),
  });
}

export function listAffiliateAffiliations(cursor?: string) {
  const query = new URLSearchParams({ role: "AFFILIATE", limit: "50" });
  if (cursor) query.set("cursor", cursor);
  return apiRequest<CursorPage<Affiliation>>(`/v1/affiliations?${query}`);
}

export function listMarketplace(cursor?: string) {
  const query = new URLSearchParams({ limit: "20" });
  if (cursor) query.set("cursor", cursor);
  return apiRequest<CursorPage<MarketplaceItem>>(`/v1/marketplace?${query}`);
}

export function requestAffiliation(productId: string) {
  return apiRequest<Affiliation>("/v1/affiliations", {
    method: "POST",
    body: JSON.stringify({ productId }),
  });
}
