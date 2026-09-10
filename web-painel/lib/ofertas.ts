import { apiRequest } from "./api";

export type OfferPaymentMethod = "PIX" | "CARD" | "BOLETO";
export type BillingCycle = "MONTHLY" | "QUARTERLY" | "SEMIANNUAL" | "ANNUAL";
export type OfferPayoutDelay = "D32" | "D15" | "D7" | "D2";
export type OfferStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";
export type OfferImmutableField = "CYCLE" | "GUARANTEE";

export type Offer = {
  id: string;
  productId: string;
  chargeType: "ONE_TIME" | "SUBSCRIPTION";
  segment: "SAAS" | "DIGITAL";
  slug: string;
  priceCents: number;
  cycle: BillingCycle | null;
  trialDays: number;
  trialRequiresCard: boolean;
  guaranteeDays: number;
  maxInstallments: number;
  boletoDueDays: number;
  boletoAdvanceDays: number;
  paymentMethods: OfferPaymentMethod[];
  payoutDelay: OfferPayoutDelay;
  status: OfferStatus;
  availableAt: string;
  immutableFields: OfferImmutableField[];
  createdAt: string;
  updatedAt: string;
};

export type OfferInput = {
  priceCents: number;
  cycle: BillingCycle | null;
  trialDays: number;
  trialRequiresCard: boolean;
  guaranteeDays: number;
  maxInstallments: number;
  boletoDueDays: number;
  boletoAdvanceDays: number;
  paymentMethods: OfferPaymentMethod[];
  payoutDelay: OfferPayoutDelay;
};

export type OfferInputErrors = Partial<Record<keyof OfferInput | "price", string>>;

export type OfferSimulation = {
  grossCents: number;
  discountCents: number;
  paidCents: number;
  platformFeeCents: number;
  providerCostCents: number;
  commissionCents: number;
  sellerCents: number;
  availableAt: string;
};

export type Publication = {
  published: boolean;
  requiredAction: "COMPLETE_KYC" | "CONFIGURE_FISCAL" | null;
  actionUrl: string | null;
  offer: Offer;
};

export function parseMoneyToCents(value: string): number | null {
  const raw = value.trim().replace(/^R\$\s*/i, "").replace(/\s/g, "");
  if (!raw) return null;
  const normalized = raw.includes(",") ? raw.replace(/\./g, "").replace(",", ".") : raw;
  if (!/^\d+(\.\d{1,2})?$/.test(normalized)) return null;
  const [whole, fraction = ""] = normalized.split(".");
  const cents = BigInt(whole) * 100n + BigInt(fraction.padEnd(2, "0"));
  if (cents > BigInt(Number.MAX_SAFE_INTEGER)) return null;
  return Number(cents);
}

export function formatOfferMoney(cents: number): string {
  return new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(cents / 100);
}

export function validateOfferInput(input: OfferInput, context: { segment: "SAAS" | "DIGITAL"; chargeType: "ONE_TIME" | "SUBSCRIPTION" }): OfferInputErrors {
  const errors: OfferInputErrors = {};
  if (!Number.isSafeInteger(input.priceCents) || input.priceCents < 2_000) errors.price = "Informe um preço mínimo de R$ 20,00.";
  if (context.chargeType === "SUBSCRIPTION" && !input.cycle) errors.cycle = "Escolha o ciclo da assinatura.";
  if (context.chargeType === "ONE_TIME" && input.cycle) errors.cycle = "Pagamento único não aceita ciclo.";
  if (!Number.isInteger(input.trialDays) || input.trialDays < 0 || input.trialDays > 30) errors.trialDays = "Use um período entre 0 e 30 dias.";
  if (!input.trialRequiresCard && context.segment !== "SAAS") errors.trialRequiresCard = "Teste sem cartão está disponível apenas para SaaS.";
  if (!Number.isInteger(input.guaranteeDays) || input.guaranteeDays < 7) errors.guaranteeDays = "A garantia mínima é de 7 dias.";
  if (!Number.isInteger(input.maxInstallments) || input.maxInstallments < 1 || input.maxInstallments > 12) errors.maxInstallments = "Use entre 1 e 12 parcelas.";
  if (!Number.isInteger(input.boletoDueDays) || input.boletoDueDays < 1 || input.boletoDueDays > 15) errors.boletoDueDays = "Use entre 1 e 15 dias.";
  if (!Number.isInteger(input.boletoAdvanceDays) || input.boletoAdvanceDays < 3 || input.boletoAdvanceDays > 10) errors.boletoAdvanceDays = "Use entre 3 e 10 dias.";
  if (!input.paymentMethods.length) errors.paymentMethods = "Escolha ao menos um meio de pagamento.";
  if (context.segment !== "SAAS" && input.paymentMethods.includes("BOLETO")) errors.paymentMethods = "Boleto está disponível apenas para SaaS.";
  return errors;
}

export function listOffers(productId: string) {
  return apiRequest<Offer[]>(`/v1/products/${encodeURIComponent(productId)}/offers`);
}

export function getOffer(offerId: string) {
  return apiRequest<Offer>(`/v1/offers/${encodeURIComponent(offerId)}`);
}

export function createOffer(productId: string, input: OfferInput) {
  return apiRequest<Offer>(`/v1/products/${encodeURIComponent(productId)}/offers`, { method: "POST", body: JSON.stringify(input) });
}

export function updateOffer(offerId: string, input: OfferInput) {
  return apiRequest<Offer>(`/v1/offers/${encodeURIComponent(offerId)}`, { method: "PUT", body: JSON.stringify(input) });
}

export function publishOffer(offerId: string) {
  return apiRequest<Publication>(`/v1/offers/${encodeURIComponent(offerId)}/publish`, { method: "POST" });
}

export function simulateOffer(offerId: string, method: OfferPaymentMethod, installments: number, couponCode?: string) {
  return apiRequest<OfferSimulation>(`/v1/offers/${encodeURIComponent(offerId)}/simulation`, {
    method: "POST",
    body: JSON.stringify({ method, installments, couponCode: couponCode?.trim() || undefined }),
  });
}
