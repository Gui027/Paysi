import assert from "node:assert/strict";
import { afterEach, test } from "node:test";
import { ApiRequestError } from "./api";
import {
  cancelSubscription,
  getSubscription,
  isTrialWithoutCard,
  listSubscriptions,
  Subscription,
  SubscriptionDetail,
} from "./assinaturas";

const trialWithoutCard: Subscription = {
  id: "sub-1",
  orderId: "ord-1",
  offerId: "off-1",
  status: "TRIAL",
  cycleNumber: 0,
  trialEndsAt: "2026-09-20T00:00:00Z",
  nextChargeAt: "2026-09-20T00:00:00Z",
  canceledAt: null,
  cancelPending: false,
  hasPaymentMethod: false,
  createdAt: "2026-09-10T00:00:00Z",
};

const trialWithCard: Subscription = { ...trialWithoutCard, hasPaymentMethod: true };
const active: Subscription = { ...trialWithoutCard, status: "ACTIVE", hasPaymentMethod: true, cycleNumber: 2 };

const sampleDetail: SubscriptionDetail = {
  subscription: active,
  charges: [
    {
      id: "charge-1",
      cycleNumber: 2,
      amountCents: 9900,
      status: "FAILED",
      attemptCount: 2,
      nextRetryAt: "2026-09-13T00:00:00Z",
      paidAt: null,
      createdAt: "2026-09-10T00:00:00Z",
    },
  ],
};

const originalFetch = globalThis.fetch;
afterEach(() => {
  globalThis.fetch = originalFetch;
});

test("diferencia teste grátis sem cartão de assinatura normal", () => {
  assert.equal(isTrialWithoutCard(trialWithoutCard), true);
  assert.equal(isTrialWithoutCard(trialWithCard), false);
  assert.equal(isTrialWithoutCard(active), false);
});

test("lista assinaturas com paginação por cursor", async () => {
  let requestedUrl = "";
  globalThis.fetch = (async (input) => {
    requestedUrl = String(input);
    return new Response(JSON.stringify({ items: [active], nextCursor: "page-2" }), {
      status: 200,
      headers: { "content-type": "application/json" },
    });
  }) as typeof fetch;

  const page = await listSubscriptions("cursor-1");
  assert.equal(page.items.length, 1);
  assert.equal(page.nextCursor, "page-2");
  assert.match(requestedUrl, /cursor=cursor-1/);
  assert.match(requestedUrl, /\/v1\/accounts\/me\/subscriptions/);
});

test("busca detalhe da assinatura com histórico de cobranças e tentativas", async () => {
  globalThis.fetch = (async () =>
    new Response(JSON.stringify(sampleDetail), {
      status: 200,
      headers: { "content-type": "application/json" },
    })) as typeof fetch;

  const detail = await getSubscription("sub-1");
  assert.equal(detail.subscription.id, "sub-1");
  assert.equal(detail.charges[0].attemptCount, 2);
  assert.equal(detail.charges[0].status, "FAILED");
});

test("cancela assinatura via POST e propaga erro quando já não existe", async () => {
  let calledMethod = "";
  globalThis.fetch = (async (_input, init) => {
    calledMethod = init?.method ?? "";
    return new Response(null, { status: 204 });
  }) as typeof fetch;

  await cancelSubscription("sub-1");
  assert.equal(calledMethod, "POST");

  globalThis.fetch = (async () =>
    new Response(JSON.stringify({ code: "SUBSCRIPTION_NOT_FOUND", message: "não encontrada" }), {
      status: 404,
      headers: { "content-type": "application/json" },
    })) as typeof fetch;

  await assert.rejects(
    () => cancelSubscription("sub-inexistente"),
    (err: unknown) => err instanceof ApiRequestError && err.status === 404
  );
});
