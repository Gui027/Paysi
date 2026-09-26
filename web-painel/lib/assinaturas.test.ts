import assert from "node:assert/strict";
import test from "node:test";
import { cycleUnit, emptySubscriptionsQuery, planName, statusText, subscriptionsParams } from "./assinaturas";

test("monta a query da lista de assinaturas com aba, busca, filtros e página", () => {
  const params = subscriptionsParams({ ...emptySubscriptionsQuery, tab: "all", q: " vera ", statuses: ["ACTIVE", "PAST_DUE"], cycle: "MONTHLY", method: "PIX", productId: "p1", from: "2026-09-01", to: "2026-09-30", page: 2 });
  assert.equal(params.get("tab"), "all");
  assert.equal(params.get("q"), "vera");
  assert.deepEqual(params.getAll("status"), ["ACTIVE", "PAST_DUE"]);
  assert.equal(params.get("cycle"), "MONTHLY");
  assert.equal(params.get("method"), "PIX");
  assert.equal(params.get("productId"), "p1");
  assert.equal(params.get("page"), "2");
  assert.equal(subscriptionsParams(emptySubscriptionsQuery, false).has("page"), false);
  assert.equal(subscriptionsParams(emptySubscriptionsQuery).get("tab"), "active");
});

test("status na lista mostra o cancelamento agendado e o plano cai no nome da frequência", () => {
  assert.equal(statusText({ status: "ACTIVE", cancelPending: false }), "Ativo");
  assert.equal(statusText({ status: "ACTIVE", cancelPending: true }), "Cancelamento agendado");
  assert.equal(statusText({ status: "CANCELED", cancelPending: false }), "Cancelado");
  assert.equal(statusText({ status: "PAST_DUE", cancelPending: false }), "Em atraso");
  assert.equal(planName({ offerName: "  Plano Pro ", cycle: "MONTHLY" }), "Plano Pro");
  assert.equal(planName({ offerName: null, cycle: "MONTHLY" }), "Plano Mensal");
  assert.equal(cycleUnit.ANNUAL, "ano");
});
