import assert from "node:assert/strict";
import { afterEach, test } from "node:test";
import { ApiRequestError } from "./api";
import {
  BalanceView,
  DashboardView,
  getBalance,
  getDashboard,
} from "./dashboard";

const balance: BalanceView = { guarantee: 1000, pending: 2000, reserve: 300, available: 5000, debt: 0, asOf: "2026-09-03T12:00:00Z" };
const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });

test("busca o saldo dos cinco buckets na rota autenticada", async () => {
  let requestedUrl = "";
  globalThis.fetch = (async input => {
    requestedUrl = String(input);
    return new Response(JSON.stringify(balance), { status: 200, headers: { "content-type": "application/json" } });
  }) as typeof fetch;

  const result = await getBalance();
  assert.equal(result.available, 5000);
  assert.equal(requestedUrl, "/api/v1/accounts/me/balance");
});

test("propaga ApiRequestError quando a chamada de saldo falha", async () => {
  globalThis.fetch = (async () => new Response(JSON.stringify({ code: "UNAUTHORIZED", message: "Sessão expirada" }), { status: 401, headers: { "content-type": "application/json" } })) as typeof fetch;
  await assert.rejects(() => getBalance(), (error: unknown) => error instanceof ApiRequestError && error.status === 401);
});

function dashboard(): DashboardView {
  return {
    period: { preset: "7d", from: "2026-09-01T00:00:00Z", to: "2026-09-08T12:00:00Z" },
    salesToday: { state: "SUCCESS", data: { amountCents: 12345, count: 2 } },
    balance: { state: "SUCCESS", data: balance },
    nextReceivables: { state: "EMPTY", data: [] },
    subscriptions: { state: "ERROR", code: "DASHBOARD_BLOCK_UNAVAILABLE", message: "Bloco indisponível" },
    alerts: { state: "SUCCESS", data: [] },
    recentSales: { state: "SUCCESS", data: [] },
  };
}

test("busca o dashboard agregado usando o período servido pela API", async () => {
  let requestedUrl = "";
  globalThis.fetch = (async input => {
    requestedUrl = String(input);
    return new Response(JSON.stringify(dashboard()), { status: 200, headers: { "content-type": "application/json" } });
  }) as typeof fetch;

  const result = await getDashboard("7d");
  assert.equal(result.salesToday.data?.amountCents, 12345);
  assert.equal(result.subscriptions.state, "ERROR");
  assert.equal(requestedUrl, "/api/v1/accounts/me/dashboard?period=7d");
});

test("mantém estados vazios e erros parciais no contrato do dashboard", () => {
  const result = dashboard();
  assert.equal(result.nextReceivables.state, "EMPTY");
  assert.equal(result.subscriptions.state, "ERROR");
  assert.equal(result.salesToday.data?.count, 2);
});
