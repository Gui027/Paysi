import assert from "node:assert/strict";
import { afterEach, test } from "node:test";
import { ApiRequestError } from "./api";
import {
  createWebhookEndpoint,
  deliveryStatusLabel,
  listWebhookDeliveries,
  listWebhookEndpoints,
  resendWebhookDelivery,
  rotateWebhookSecret,
  updateWebhookEndpoint,
  WebhookDelivery,
} from "./integracoes";

const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });

test("lista endpoints cadastrados", async () => {
  globalThis.fetch = (async () => new Response(JSON.stringify([
    { id: "ep-1", url: "https://example.com/hook", events: ["ORDER_PAID"], enabled: true, secretRotatedAt: null, createdAt: "2026-09-01T00:00:00Z" },
  ]), { status: 200, headers: { "content-type": "application/json" } })) as typeof fetch;

  const endpoints = await listWebhookEndpoints();
  assert.equal(endpoints.length, 1);
  assert.equal(endpoints[0].url, "https://example.com/hook");
});

test("cria endpoint e devolve o segredo uma única vez", async () => {
  let body = "";
  globalThis.fetch = (async (_input, init) => {
    body = String(init?.body);
    return new Response(JSON.stringify({
      endpoint: { id: "ep-1", url: "https://example.com/hook", events: ["ORDER_PAID"], enabled: true, secretRotatedAt: null, createdAt: "2026-09-01T00:00:00Z" },
      secret: "raw-secret-value",
    }), { status: 201, headers: { "content-type": "application/json" } });
  }) as typeof fetch;

  const created = await createWebhookEndpoint("https://example.com/hook", ["ORDER_PAID"], true);
  assert.equal(created.secret, "raw-secret-value");
  assert.deepEqual(JSON.parse(body), { url: "https://example.com/hook", events: ["ORDER_PAID"], enabled: true });
});

test("atualiza endpoint existente", async () => {
  let requestedUrl = "";
  let method = "";
  globalThis.fetch = (async (input, init) => {
    requestedUrl = String(input);
    method = init?.method ?? "";
    return new Response(JSON.stringify({ id: "ep-1", url: "https://new.example.com", events: ["ORDER_PAID"], enabled: false, secretRotatedAt: null, createdAt: "2026-09-01T00:00:00Z" }), {
      status: 200, headers: { "content-type": "application/json" },
    });
  }) as typeof fetch;

  await updateWebhookEndpoint("ep-1", "https://new.example.com", ["ORDER_PAID"], false);
  assert.equal(method, "PUT");
  assert.match(requestedUrl, /\/v1\/accounts\/me\/webhooks\/ep-1$/);
});

test("rotaciona segredo e mantém o anterior válido por 24h", async () => {
  globalThis.fetch = (async () => new Response(JSON.stringify({
    endpointId: "ep-1", secret: "novo-segredo", previousSecretValidUntil: "2026-09-11T00:00:00Z",
  }), { status: 200, headers: { "content-type": "application/json" } })) as typeof fetch;

  const rotated = await rotateWebhookSecret("ep-1");
  assert.equal(rotated.secret, "novo-segredo");
});

test("propaga erro quando URL do webhook é inválida", async () => {
  globalThis.fetch = (async () => new Response(JSON.stringify({ code: "WEBHOOK_URL_INVALID" }), {
    status: 400, headers: { "content-type": "application/json" },
  })) as typeof fetch;

  await assert.rejects(() => createWebhookEndpoint("not-a-url", ["ORDER_PAID"], true),
    (error: unknown) => error instanceof ApiRequestError && error.problem.code === "WEBHOOK_URL_INVALID");
});

test("lista histórico de entregas e reenvia sem duplicar", async () => {
  let calledUrl = "";
  globalThis.fetch = (async (input) => {
    calledUrl = String(input);
    return new Response(JSON.stringify([]), { status: 200, headers: { "content-type": "application/json" } });
  }) as typeof fetch;
  await listWebhookDeliveries();
  assert.match(calledUrl, /limit=50/);

  globalThis.fetch = (async () => new Response(null, { status: 202 })) as typeof fetch;
  await resendWebhookDelivery("event-1");
});

test("classifica status da entrega por código HTTP e retentativa pendente", () => {
  const base: WebhookDelivery = {
    id: "d-1", eventId: "e-1", endpointId: "ep-1", attempt: 1,
    httpStatus: null, error: null, nextRetryAt: null, createdAt: "2026-09-01T00:00:00Z",
  };
  assert.equal(deliveryStatusLabel({ ...base, httpStatus: 200 }), "Entregue");
  assert.equal(deliveryStatusLabel({ ...base, httpStatus: 500, nextRetryAt: "2026-09-02T00:00:00Z" }), "Vai tentar de novo");
  assert.equal(deliveryStatusLabel({ ...base, httpStatus: 500 }), "Falhou");
});
