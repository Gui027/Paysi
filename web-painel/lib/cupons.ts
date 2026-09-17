import { apiRequest } from "./api";
import { listOffers, OfferStatus } from "./ofertas";
import { listProducts } from "./produtos";

export type CouponKind = "PERCENT" | "FIXED";

export type Coupon = {
  id: string;
  code: string;
  discountType: CouponKind;
  discountBps: number | null;
  discountCents: number | null;
  startsAt: string | null;
  expiresAt: string | null;
  maxRedemptions: number | null;
  maxPerBuyer: number;
  redeemedCount: number;
  offerIds: string[];
  createdAt: string;
};

export type CouponInput = {
  code: string;
  discountType: CouponKind;
  discountBps: number | null;
  discountCents: number | null;
  startsAt: string | null;
  expiresAt: string | null;
  maxRedemptions: number | null;
  maxPerBuyer: number;
  offerIds: string[];
};

export type CouponInputErrors = Partial<Record<keyof CouponInput | "value", string>>;

export type CouponDisplayStatus = "SCHEDULED" | "ACTIVE" | "EXPIRED" | "EXHAUSTED" | "ARCHIVED";

export const couponStatusLabel: Record<CouponDisplayStatus, string> = {
  SCHEDULED: "Agendado",
  ACTIVE: "Ativo",
  EXPIRED: "Expirado",
  EXHAUSTED: "Esgotado",
  ARCHIVED: "Arquivado",
};

export const couponStatusTone: Record<CouponDisplayStatus, "neutral" | "success" | "warning" | "danger"> = {
  SCHEDULED: "neutral",
  ACTIVE: "success",
  EXPIRED: "danger",
  EXHAUSTED: "danger",
  ARCHIVED: "neutral",
};

const CODE_PATTERN = /^[A-Z0-9][A-Z0-9_-]{2,31}$/;

/** Mesma janela de validade usada pelo backend (Coupon.activeAt), só que detalhada para exibição. */
export function couponStatus(coupon: Coupon, archived: boolean, now = new Date()): CouponDisplayStatus {
  if (archived) return "ARCHIVED";
  if (coupon.startsAt && now < new Date(coupon.startsAt)) return "SCHEDULED";
  if (coupon.expiresAt && now >= new Date(coupon.expiresAt)) return "EXPIRED";
  if (coupon.maxRedemptions !== null && coupon.redeemedCount >= coupon.maxRedemptions) return "EXHAUSTED";
  return "ACTIVE";
}

export function validateCouponInput(input: CouponInput): CouponInputErrors {
  const errors: CouponInputErrors = {};
  if (!CODE_PATTERN.test(input.code)) errors.code = "Use de 3 a 32 letras, números, '-' ou '_'.";
  if (input.discountType === "PERCENT") {
    if (input.discountBps === null || input.discountBps < 1 || input.discountBps > 10_000) errors.value = "Informe um percentual entre 0,01% e 100%.";
  } else if (input.discountCents === null || input.discountCents < 1) {
    errors.value = "Informe um valor fixo maior que zero.";
  }
  if (input.startsAt && input.expiresAt && new Date(input.startsAt) >= new Date(input.expiresAt)) {
    errors.expiresAt = "O vencimento deve ser depois do início.";
  }
  if (input.maxRedemptions !== null && (!Number.isInteger(input.maxRedemptions) || input.maxRedemptions < 1)) {
    errors.maxRedemptions = "Use um número inteiro maior que zero.";
  }
  if (!Number.isInteger(input.maxPerBuyer) || input.maxPerBuyer < 1) {
    errors.maxPerBuyer = "Use um número inteiro maior que zero.";
  }
  if (!input.offerIds.length) errors.offerIds = "Selecione ao menos uma oferta.";
  return errors;
}

export function parsePercentToBps(value: string): number | null {
  const normalized = value.trim().replace(",", ".");
  if (!/^\d{1,3}(?:\.\d{1,2})?$/.test(normalized)) return null;
  const [wholeText, decimalText = ""] = normalized.split(".");
  const bps = Number(wholeText) * 100 + Number(decimalText.padEnd(2, "0"));
  return bps >= 1 && bps <= 10_000 ? bps : null;
}

export function formatBps(bps: number): string {
  const whole = Math.trunc(bps / 100);
  const decimal = String(bps % 100).padStart(2, "0").replace(/0+$/, "");
  return `${whole}${decimal ? `,${decimal}` : ""}%`;
}

export { formatOfferMoney as formatFixedDiscount, parseMoneyToCents as parseFixedDiscount } from "./ofertas";

export function toDatetimeLocal(iso: string | null): string {
  if (!iso) return "";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "";
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function fromDatetimeLocal(value: string): string | null {
  if (!value.trim()) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

export type OfferOption = {
  id: string;
  label: string;
  status: OfferStatus;
};

/** Não há endpoint dedicado para listar ofertas do vendedor; agrega por produto. */
export async function listOfferOptions(): Promise<OfferOption[]> {
  const options: OfferOption[] = [];
  let cursor: string | undefined;
  do {
    const page = await listProducts(cursor);
    for (const product of page.items) {
      const offers = await listOffers(product.id);
      for (const offer of offers) {
        options.push({ id: offer.id, label: `${product.name} — ${offer.slug}`, status: offer.status });
      }
    }
    cursor = page.nextCursor ?? undefined;
  } while (cursor);
  return options;
}

export function listCoupons() {
  return apiRequest<Coupon[]>("/v1/coupons");
}

export function getCoupon(couponId: string) {
  return apiRequest<Coupon>(`/v1/coupons/${encodeURIComponent(couponId)}`);
}

export function createCoupon(input: CouponInput) {
  return apiRequest<Coupon>("/v1/coupons", { method: "POST", body: JSON.stringify(input) });
}

export function updateCoupon(couponId: string, input: CouponInput) {
  return apiRequest<Coupon>(`/v1/coupons/${encodeURIComponent(couponId)}`, { method: "PUT", body: JSON.stringify(input) });
}

export function archiveCoupon(couponId: string) {
  return apiRequest<void>(`/v1/coupons/${encodeURIComponent(couponId)}`, { method: "DELETE" });
}
