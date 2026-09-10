import assert from "node:assert/strict";
import { afterEach, test } from "node:test";
import { ApiRequestError } from "./api.js";
import { simularCheckout } from "./simulacao.js";

const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });

test("envia cupom e forma de pagamento para a simulação pública", async () => {
  let requestedUrl = "";
  let requestedBody = "";
  globalThis.fetch = (async (input, init) => {
    requestedUrl = String(input);
    requestedBody = String(init?.body);
    return new Response(JSON.stringify({
      grossCents: 20_000,
      discountCents: 2_000,
      paidCents: 18_000,
      providerFeeCents: 0,
      sellerFeeCents: 0,
      affiliateFeeCents: 0,
      sellerAmountCents: 18_000,
      availableAt: "2026-09-14T00:00:00Z",
    }), { status: 200, headers: { "content-type": "application/json" } });
  }) as typeof fetch;

  const result = await simularCheckout("crm pro/2026", {
    method: "CARD",
    installments: 3,
    couponCode: "BEMVINDO10",
  });

  assert.match(requestedUrl, /\/v1\/checkout\/crm%20pro%2F2026\/simulation$/);
  assert.deepEqual(JSON.parse(requestedBody), {
    method: "CARD",
    installments: 3,
    couponCode: "BEMVINDO10",
  });
  assert.equal(result.discountCents, 2_000);
  assert.equal(result.paidCents, 18_000);
});

test("propaga o código estável de cupom recusado", async () => {
  globalThis.fetch = (async () => new Response(JSON.stringify({
    code: "COUPON_EXPIRED",
    message: "Este cupom expirou.",
  }), { status: 422, headers: { "content-type": "application/json" } })) as typeof fetch;

  await assert.rejects(
    () => simularCheckout("oferta", { method: "PIX", installments: 1, couponCode: "EXPIRADO" }),
    (error: unknown) => error instanceof ApiRequestError
      && error.status === 422
      && error.problem.code === "COUPON_EXPIRED",
  );
});
