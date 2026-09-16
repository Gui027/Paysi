import assert from "node:assert/strict";
import { afterEach, test } from "node:test";
import { ApiRequestError } from "./api";
import {
  cancelSubscription,
  dunningDayLabel,
  getSubscription,
  listSubscriptions,
  subscriptionMatchesFilters,
  SubscriptionDetail,
  SubscriptionSummary,
} from "./assinaturas";

const sampleSub: SubscriptionSummary = {
  id: "sub-abc-123",
  orderId: "ord-xyz-456",
  buyerNameMasked: "Maria S***",
  productName: "Curso Pro Anual",
  productId: "prod-111",
  offerId: "off-222",
  offerTitle: "Plano Anual",
  status: "ACTIVE",
  method: "CARD",
  priceCents: 29900,
  cycle: "YEARLY",
  trialEnd: null,
  nextChargeAt: "2027-09-16T00:00:00Z",
  nextRetryAt: null,
  cancelAtPeriodEnd: false,
  attemptCount: 0,
  createdAt: "2026-09-16T10:00:00Z",
};

const sampleDetail: SubscriptionDetail = {
  ...sampleSub,
  guaranteeDays: 30,
  affiliateName: "João Afiliado",
  charges: [
    {
      id: "chg-cycle-1",
      cycleNumber: 1,
      status: "APPROVED",
      method: "CARD",
      amountCents: 29900,
      occurredAt: "2026-09-16T10:05:00Z",
    },
  ],
};

const originalFetch = globalThis.fetch;
afterEach(() => {
  globalThis.fetch = originalFetch;
});

test("rótulo de régua D+1/3/7/14 conforme tentativa atual", () => {
  assert.equal(dunningDayLabel(0), "D+1");
  assert.equal(dunningDayLabel(1), "D+3");
  assert.equal(dunningDayLabel(2), "D+7");
  assert.equal(dunningDayLabel(3), "D+14");
  assert.equal(dunningDayLabel(4), "Encerrado");
  assert.equal(dunningDayLabel(99), "Encerrado");
});

test("filtra assinaturas por termo, status, método e produto", () => {
  assert.equal(
    subscriptionMatchesFilters(sampleSub, {
      query: "curso",
      status: "ACTIVE",
      method: "CARD",
      productId: "prod-111",
    }),
    true,
  );

  // query não bate
  assert.equal(
    subscriptionMatchesFilters(sampleSub, {
      query: "boleto",
      status: "",
      method: "",
      productId: "",
    }),
    false,
  );

  // status diferente
  assert.equal(
    subscriptionMatchesFilters(sampleSub, {
      query: "",
      status: "CANCELED",
      method: "",
      productId: "",
    }),
    false,
  );

  // método diferente
  assert.equal(
    subscriptionMatchesFilters(sampleSub, {
      query: "",
      status: "",
      method: "BOLETO",
      productId: "",
    }),
    false,
  );

  // produto diferente
  assert.equal(
    subscriptionMatchesFilters(sampleSub, {
      query: "",
      status: "",
      method: "",
      productId: "prod-outro",
    }),
    false,
  );

  // sem filtros — sempre bate
  assert.equal(
    subscriptionMatchesFilters(sampleSub, {
      query: "",
      status: "",
      method: "",
      productId: "",
    }),
    true,
  );
});

test("filtra por id parcial da assinatura", () => {
  assert.equal(
    subscriptionMatchesFilters(sampleSub, {
      query: "sub-abc",
      status: "",
      method: "",
      productId: "",
    }),
    true,
  );
});

test("lista assinaturas com paginação por cursor e filtros", async () => {
  let requestedUrl = "";
  globalThis.fetch = (async (input) => {
    requestedUrl = String(input);
    return new Response(
      JSON.stringify({ items: [sampleSub], nextCursor: "page-2" }),
      { status: 200, headers: { "content-type": "application/json" } },
    );
  }) as typeof fetch;

  const page = await listSubscriptions(
    { status: "ACTIVE", method: "CARD" },
    "cursor-1",
  );
  assert.equal(page.items.length, 1);
  assert.equal(page.items[0].id, "sub-abc-123");
  assert.equal(page.nextCursor, "page-2");
  assert.match(requestedUrl, /cursor=cursor-1/);
  assert.match(requestedUrl, /status=ACTIVE/);
  assert.match(requestedUrl, /method=CARD/);
});

test("busca detalhe da assinatura com ciclos e afiliado", async () => {
  globalThis.fetch = (async () =>
    new Response(JSON.stringify(sampleDetail), {
      status: 200,
      headers: { "content-type": "application/json" },
    })) as typeof fetch;

  const detail = await getSubscription("sub-abc-123");
  assert.equal(detail.id, "sub-abc-123");
  assert.equal(detail.charges.length, 1);
  assert.equal(detail.charges[0].cycleNumber, 1);
  assert.equal(detail.affiliateName, "João Afiliado");
  assert.equal(detail.guaranteeDays, 30);
});

test("cancela assinatura com POST e retorna 204", async () => {
  let method = "";
  let url = "";
  globalThis.fetch = (async (input, init) => {
    url = String(input);
    method = (init as RequestInit)?.method ?? "GET";
    return new Response(null, { status: 204 });
  }) as typeof fetch;

  await cancelSubscription("sub-abc-123");
  assert.equal(method, "POST");
  assert.match(url, /subscriptions\/sub-abc-123\/cancel/);
});

test("propaga 404 quando assinatura não encontrada", async () => {
  globalThis.fetch = (async () =>
    new Response(
      JSON.stringify({
        code: "SUBSCRIPTION_NOT_FOUND",
        message: "Assinatura não encontrada",
      }),
      { status: 404, headers: { "content-type": "application/json" } },
    )) as typeof fetch;

  await assert.rejects(
    () => getSubscription("sub-desconhecida"),
    (err: unknown) => err instanceof ApiRequestError && err.status === 404,
  );
});
