import assert from "node:assert/strict";
import { afterEach, test } from "node:test";
import {
  CobrancaIniciada,
  cobrancaAprovada,
  cobrancaRecusada,
  confirmarTresDs,
  exigeDesafioTresDs,
  iniciarCobranca,
} from "./cobranca.js";
import { ApiRequestError } from "./api.js";

const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });

const base: CobrancaIniciada = {
  chargeId: "charge-1",
  method: "CARD",
  status: "approved",
  idempotentReplay: false,
  threeDs: null,
  boletoBarcode: null,
  boletoUrl: null,
  pixQrCode: null,
  expiresAt: null,
};

test("classifica aprovação, recusa e exigência de 3DS", () => {
  assert.equal(cobrancaAprovada(base), true);
  assert.equal(cobrancaRecusada(base), false);
  assert.equal(cobrancaAprovada({ ...base, status: "declined" }), false);
  assert.equal(cobrancaRecusada({ ...base, status: "declined" }), true);
  assert.equal(cobrancaRecusada({ ...base, status: "FAILED" }), true);
  assert.equal(exigeDesafioTresDs(base), false);
  assert.equal(
    exigeDesafioTresDs({ ...base, threeDs: { required: true, status: "CHALLENGE_REQUIRED", challengeUrl: "https://3ds" } }),
    true
  );
  assert.equal(
    exigeDesafioTresDs({ ...base, threeDs: { required: true, status: "CHALLENGE_REQUIRED", challengeUrl: null } }),
    false
  );
});

test("inicia cobrança enviando token, evidência e aceite dos termos", async () => {
  let requestedUrl = "";
  let body = "";
  globalThis.fetch = (async (input, init) => {
    requestedUrl = String(input);
    body = String(init?.body);
    return new Response(JSON.stringify(base), { status: 200, headers: { "content-type": "application/json" } });
  }) as typeof fetch;

  const result = await iniciarCobranca("order-1", {
    cardToken: "tok_1",
    deviceKey: "device-1",
    termsHash: "sha256:abc",
    termsAcceptedAt: "2026-09-01T00:00:00Z",
  });

  assert.equal(result.chargeId, "charge-1");
  assert.match(requestedUrl, /\/v1\/orders\/order-1\/charge$/);
  assert.deepEqual(JSON.parse(body), {
    cardToken: "tok_1",
    deviceKey: "device-1",
    termsHash: "sha256:abc",
    termsAcceptedAt: "2026-09-01T00:00:00Z",
  });
});

test("confirma desafio 3DS e propaga recusa do provedor", async () => {
  globalThis.fetch = (async () => new Response(JSON.stringify({ code: "THREE_DS_NOT_PENDING" }), {
    status: 409, headers: { "content-type": "application/json" },
  })) as typeof fetch;

  await assert.rejects(
    () => confirmarTresDs("charge-1", {
      challengeToken: "tok-3ds", deviceKey: "device-1",
      termsHash: "sha256:abc", termsAcceptedAt: "2026-09-01T00:00:00Z",
    }),
    (error: unknown) => error instanceof ApiRequestError && error.problem.code === "THREE_DS_NOT_PENDING"
  );
});
