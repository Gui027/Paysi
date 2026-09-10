import { apiRequest } from "./api.js";

export type Segment = "SAAS" | "DIGITAL";
export type ChargeType = "ONE_TIME" | "SUBSCRIPTION";
export type BillingCycle = "MONTHLY" | "QUARTERLY" | "SEMIANNUAL" | "ANNUAL";
export type PaymentMethod = "PIX" | "CARD" | "BOLETO";
export type PersonType = "PF" | "PJ";

export type CheckoutAppearance = {
  logoUrl: string | null;
  bannerUrl: string | null;
  sideImageUrl: string | null;
  primaryColor: string;
  buttonText: string;
};

export type CheckoutLegalTexts = {
  termsUrl: string;
  privacyUrl: string;
};

export type CheckoutContract = {
  product: string;
  segment: Segment;
  chargeType: ChargeType;
  priceCents: number;
  cycle: BillingCycle | null;
  today: string;
  nextChargeAt: string | null;
  methods: PaymentMethod[];
  installments: number;
  requiredBuyerFields: Record<PersonType, string[]>;
  appearance: CheckoutAppearance;
  legalTexts: CheckoutLegalTexts;
};

export const cycleLabel: Record<BillingCycle, string> = {
  MONTHLY: "mensal",
  QUARTERLY: "trimestral",
  SEMIANNUAL: "semestral",
  ANNUAL: "anual",
};

export function getCheckoutContract(slug: string) {
  return apiRequest<CheckoutContract>(`/v1/offers/${encodeURIComponent(slug)}/checkout`);
}
