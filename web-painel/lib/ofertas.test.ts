import assert from "node:assert/strict";
import { afterEach, test } from "node:test";
import { ApiRequestError } from "./api";
import { getOffer, parseMoneyToCents, simulateOffer, validateOfferInput } from "./ofertas";

const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });

test("converte valores brasileiros em centavos sem usar taxa local", () => {
  assert.equal(parseMoneyToCents("20,00"), 2000);
  assert.equal(parseMoneyToCents("1.234,56"), 123456);
  assert.equal(parseMoneyToCents("R$ 20.5"), 2050);
  assert.equal(parseMoneyToCents("20,999"), null);
  assert.equal(parseMoneyToCents("abc"), null);
});

test("bloqueia combinações inválidas antes da API", () => {
  const errors = validateOfferInput({
    priceCents: 1000,
    cycle: "MONTHLY",
    trialDays: 0,
    trialRequiresCard: false,
    guaranteeDays: 3,
    maxInstallments: 13,
    boletoDueDays: 1,
    boletoAdvanceDays: 3,
    paymentMethods: ["BOLETO"],
    payoutDelay: "D7",
  }, { segment: "DIGITAL", chargeType: "ONE_TIME" });
  assert.ok(errors.price);
  assert.ok(errors.cycle);
  assert.ok(errors.trialRequiresCard);
  assert.ok(errors.guaranteeDays);
  assert.ok(errors.maxInstallments);
  assert.ok(errors.paymentMethods);
});

test("consulta a simulação no endpoint autenticado", async () => {
  let requestedUrl = "";
  let body = "";
  globalThis.fetch = (async (request, init) => {
    requestedUrl = String(request);
    body = String(init?.body);
    return new Response(JSON.stringify({ grossCents: 2000, discountCents: 0, paidCents: 2000, platformFeeCents: 319, providerCostCents: 199, commissionCents: 0, sellerCents: 1681, availableAt: "2026-10-01T00:00:00Z" }), { status: 200, headers: { "content-type": "application/json" } });
  }) as typeof fetch;

  const result = await simulateOffer("offer-1", "PIX", 1);
  assert.equal(result.sellerCents, 1681);
  assert.equal(requestedUrl, "/api/v1/offers/offer-1/simulation");
  assert.deepEqual(JSON.parse(body), { method: "PIX", installments: 1 });
});

test("propaga falha de autorização ao carregar oferta", async () => {
  globalThis.fetch = (async () => new Response(JSON.stringify({ code: "OFFER_NOT_FOUND", message: "Oferta não encontrada" }), { status: 404, headers: { "content-type": "application/json" } })) as typeof fetch;
  await assert.rejects(() => getOffer("other-offer"), (error: unknown) => error instanceof ApiRequestError && error.status === 404);
});
