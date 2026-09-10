import assert from "node:assert/strict";
import { afterEach, test } from "node:test";
import { ApiRequestError } from "./api.js";
import { getCheckoutContract } from "./checkout.js";

const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });

test("busca o contrato público pelo slug da oferta", async () => {
  let requestedUrl = "";
  globalThis.fetch = (async input => {
    requestedUrl = String(input);
    return new Response(JSON.stringify({ product: "Curso" }), { status: 200, headers: { "content-type": "application/json" } });
  }) as typeof fetch;

  const contract = await getCheckoutContract("crm-pro-12345678");
  assert.equal((contract as { product: string }).product, "Curso");
  assert.match(requestedUrl, /\/v1\/offers\/crm-pro-12345678\/checkout$/);
});

test("propaga 404 para oferta em rascunho ou arquivada", async () => {
  globalThis.fetch = (async () => new Response(JSON.stringify({ code: "OFFER_NOT_FOUND", message: "Oferta não encontrada" }), { status: 404, headers: { "content-type": "application/json" } })) as typeof fetch;
  await assert.rejects(() => getCheckoutContract("rascunho"),
    (error: unknown) => error instanceof ApiRequestError && error.status === 404 && error.problem.code === "OFFER_NOT_FOUND");
});
