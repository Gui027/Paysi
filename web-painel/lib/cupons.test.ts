import assert from "node:assert/strict";
import { afterEach, test } from "node:test";
import { ApiRequestError } from "./api";
import {
  archiveCoupon,
  Coupon,
  couponStatus,
  createCoupon,
  formatBps,
  fromDatetimeLocal,
  listCoupons,
  parsePercentToBps,
  toDatetimeLocal,
  validateCouponInput,
} from "./cupons";

const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });

const baseInput = {
  code: "PROMO10",
  discountType: "PERCENT" as const,
  discountBps: 1000,
  discountCents: null,
  startsAt: null,
  expiresAt: null,
  maxRedemptions: null,
  maxPerBuyer: 1,
  offerIds: ["offer-1"],
};

const baseCoupon: Coupon = {
  id: "coupon-1",
  code: "PROMO10",
  discountType: "PERCENT",
  discountBps: 1000,
  discountCents: null,
  startsAt: null,
  expiresAt: null,
  maxRedemptions: null,
  maxPerBuyer: 1,
  redeemedCount: 0,
  offerIds: ["offer-1"],
  createdAt: "2026-01-01T00:00:00Z",
};

test("converte percentual textual para basis points sem ponto flutuante", () => {
  assert.equal(parsePercentToBps("12,5"), 1250);
  assert.equal(parsePercentToBps("100"), 10_000);
  assert.equal(parsePercentToBps("0"), null);
  assert.equal(parsePercentToBps("100,01"), null);
  assert.equal(parsePercentToBps("-1"), null);
  assert.equal(formatBps(1250), "12,5%");
  assert.equal(formatBps(10_000), "100%");
});

test("converte datetime-local para ISO e de volta preservando o vazio", () => {
  assert.equal(fromDatetimeLocal(""), null);
  assert.equal(fromDatetimeLocal("not-a-date"), null);
  const iso = fromDatetimeLocal("2026-03-05T10:30");
  assert.ok(iso);
  assert.equal(toDatetimeLocal(null), "");
  assert.equal(toDatetimeLocal(iso), "2026-03-05T10:30");
});

test("percentual e valor fixo não coexistem: exige um dos dois conforme o tipo", () => {
  const percentMissing = validateCouponInput({ ...baseInput, discountBps: null });
  assert.equal(percentMissing.value, "Informe um percentual entre 0,01% e 100%.");

  const fixedOk = validateCouponInput({ ...baseInput, discountType: "FIXED", discountBps: null, discountCents: 500 });
  assert.equal(fixedOk.value, undefined);

  const fixedMissing = validateCouponInput({ ...baseInput, discountType: "FIXED", discountBps: null, discountCents: null });
  assert.equal(fixedMissing.value, "Informe um valor fixo maior que zero.");
});

test("valida código, datas, limites e ofertas", () => {
  assert.equal(validateCouponInput({ ...baseInput, code: "ab" }).code, "Use de 3 a 32 letras, números, '-' ou '_'.");
  assert.equal(validateCouponInput({ ...baseInput, startsAt: "2026-06-01T00:00:00Z", expiresAt: "2026-05-01T00:00:00Z" }).expiresAt,
    "O vencimento deve ser depois do início.");
  assert.equal(validateCouponInput({ ...baseInput, maxRedemptions: 0 }).maxRedemptions, "Use um número inteiro maior que zero.");
  assert.equal(validateCouponInput({ ...baseInput, maxPerBuyer: 0 }).maxPerBuyer, "Use um número inteiro maior que zero.");
  assert.equal(validateCouponInput({ ...baseInput, offerIds: [] }).offerIds, "Selecione ao menos uma oferta.");
  assert.deepEqual(validateCouponInput(baseInput), {});
});

test("cupom expirado ou esgotado fica visível em vez de escondido", () => {
  const now = new Date("2026-06-01T00:00:00Z");
  assert.equal(couponStatus(baseCoupon, false, now), "ACTIVE");
  assert.equal(couponStatus({ ...baseCoupon, startsAt: "2026-07-01T00:00:00Z" }, false, now), "SCHEDULED");
  assert.equal(couponStatus({ ...baseCoupon, expiresAt: "2026-05-01T00:00:00Z" }, false, now), "EXPIRED");
  assert.equal(couponStatus({ ...baseCoupon, maxRedemptions: 5, redeemedCount: 5 }, false, now), "EXHAUSTED");
  assert.equal(couponStatus(baseCoupon, true, now), "ARCHIVED");
});

test("lista cupons do vendedor", async () => {
  let requestedUrl = "";
  globalThis.fetch = (async input => {
    requestedUrl = String(input);
    return new Response(JSON.stringify([baseCoupon]), { status: 200, headers: { "content-type": "application/json" } });
  }) as typeof fetch;

  const coupons = await listCoupons();
  assert.equal(coupons.length, 1);
  assert.match(requestedUrl, /\/v1\/coupons$/);
});

test("cria cupom enviando o payload exatamente como preenchido", async () => {
  let body = "";
  globalThis.fetch = (async (_input, init) => {
    body = String(init?.body);
    return new Response(JSON.stringify(baseCoupon), { status: 201, headers: { "content-type": "application/json" } });
  }) as typeof fetch;

  await createCoupon(baseInput);
  assert.deepEqual(JSON.parse(body), baseInput);
});

test("arquivar preserva o erro quando o cupom já mudou", async () => {
  globalThis.fetch = (async () => new Response(JSON.stringify({ code: "COUPON_NOT_FOUND" }), {
    status: 404,
    headers: { "content-type": "application/json" },
  })) as typeof fetch;
  await assert.rejects(() => archiveCoupon("coupon-1"),
    (error: unknown) => error instanceof ApiRequestError && error.status === 404);
});
