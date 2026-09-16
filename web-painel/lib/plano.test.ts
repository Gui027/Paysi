import assert from "node:assert/strict";
import { afterEach, test } from "node:test";
import { ApiRequestError } from "./api";
import { getPlan, getPlanHistory, PlanView, requestPlanChange } from "./plano";

const samplePlan: PlanView = {
  currentPlan: "TRANSACIONAL",
  monthlyFee: 0,
  currentPeriodStart: "2026-09-01T00:00:00Z",
  nextBilling: "2026-10-01T00:00:00Z",
  status: "ACTIVE",
  pastDueSince: null,
  pendingPlan: null,
  pendingMonthlyFee: null,
  pendingEffectiveAt: null,
  priceTable: { TRANSACIONAL: 0, ESCALA: 19_900 },
};

const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });

test("busca o plano vigente com a tabela de preços da API", async () => {
  globalThis.fetch = (async () => new Response(JSON.stringify(samplePlan), {
    status: 200,
    headers: { "content-type": "application/json" },
  })) as typeof fetch;

  const plan = await getPlan();
  assert.equal(plan.currentPlan, "TRANSACIONAL");
  assert.equal(plan.priceTable.ESCALA, 19_900);
});

test("solicita troca de plano enviando o cartão somente quando informado", async () => {
  let body = "";
  globalThis.fetch = (async (_input, init) => {
    body = String(init?.body);
    return new Response(JSON.stringify({ ...samplePlan, pendingPlan: "ESCALA" }), {
      status: 200,
      headers: { "content-type": "application/json" },
    });
  }) as typeof fetch;

  await requestPlanChange("ESCALA", "tok_1");
  assert.deepEqual(JSON.parse(body), { plan: "ESCALA", cardToken: "tok_1" });

  await requestPlanChange("TRANSACIONAL");
  assert.deepEqual(JSON.parse(body), { plan: "TRANSACIONAL" });
});

test("propaga erro quando Escala exige cartão e nenhum foi enviado", async () => {
  globalThis.fetch = (async () => new Response(JSON.stringify({ code: "CARD_REQUIRED_FOR_ESCALA" }), {
    status: 400,
    headers: { "content-type": "application/json" },
  })) as typeof fetch;

  await assert.rejects(() => requestPlanChange("ESCALA"),
    (error: unknown) => error instanceof ApiRequestError && error.problem.code === "CARD_REQUIRED_FOR_ESCALA");
});

test("lista o histórico de trocas com limite padrão", async () => {
  let requestedUrl = "";
  globalThis.fetch = (async (input) => {
    requestedUrl = String(input);
    return new Response(JSON.stringify([
      { id: "1", fromPlan: "TRANSACIONAL", toPlan: "ESCALA", priceTable: "v1-provisional", createdAt: "2026-09-01T00:00:00Z" },
    ]), { status: 200, headers: { "content-type": "application/json" } });
  }) as typeof fetch;

  const history = await getPlanHistory();
  assert.equal(history.length, 1);
  assert.match(requestedUrl, /limit=20/);
});
