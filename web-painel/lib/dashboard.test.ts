import assert from "node:assert/strict";
import { afterEach, test } from "node:test";
import { ApiRequestError } from "./api";
import {
  BalanceView,
  KycView,
  LedgerItem,
  dashboardAlerts,
  fetchUpcomingReceivables,
  getBalance,
  getKyc,
  getLedgerEntries,
  upcomingReceivables,
} from "./dashboard";

const balance: BalanceView = { guarantee: 1000, pending: 2000, reserve: 300, available: 5000, debt: 0, asOf: "2026-09-03T12:00:00Z" };
const kycApproved: KycView = { accountId: "acc-1", kycStatus: "APPROVED", providerUrl: null, requirements: [] };
const now = new Date("2026-09-03T00:00:00Z");

function ledgerItem(overrides: Partial<LedgerItem>): LedgerItem {
  return {
    entryId: 1,
    bucket: "PENDING",
    direction: "CREDIT",
    amountCents: 10000,
    origin: "SALE",
    reason: null,
    reference: null,
    availableAt: "2026-09-10T00:00:00Z",
    createdAt: "2026-09-01T00:00:00Z",
    ...overrides,
  };
}

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

test("busca o estado de KYC na rota da conta", async () => {
  let requestedUrl = "";
  globalThis.fetch = (async input => {
    requestedUrl = String(input);
    return new Response(JSON.stringify(kycApproved), { status: 200, headers: { "content-type": "application/json" } });
  }) as typeof fetch;

  const result = await getKyc();
  assert.equal(result.kycStatus, "APPROVED");
  assert.equal(requestedUrl, "/api/v1/accounts/me");
});

test("busca o extrato do razão respeitando o limite informado", async () => {
  let requestedUrl = "";
  globalThis.fetch = (async input => {
    requestedUrl = String(input);
    return new Response(JSON.stringify({ items: [], nextCursor: null }), { status: 200, headers: { "content-type": "application/json" } });
  }) as typeof fetch;

  await getLedgerEntries(50);
  assert.equal(requestedUrl, "/api/v1/accounts/me/ledger?limit=50");
});

test("propaga ApiRequestError quando a chamada de saldo falha", async () => {
  globalThis.fetch = (async () => new Response(JSON.stringify({ code: "UNAUTHORIZED", message: "Sessão expirada" }), { status: 401, headers: { "content-type": "application/json" } })) as typeof fetch;
  await assert.rejects(() => getBalance(), (error: unknown) => error instanceof ApiRequestError && error.status === 401);
});

test("próximos recebíveis inclui só PENDING/CREDIT com disponibilidade futura", () => {
  const items = [
    ledgerItem({ entryId: 1, availableAt: "2026-09-10T00:00:00Z" }),
    ledgerItem({ entryId: 2, bucket: "AVAILABLE" }),
    ledgerItem({ entryId: 3, direction: "DEBIT" }),
    ledgerItem({ entryId: 4, availableAt: null }),
    ledgerItem({ entryId: 5, availableAt: "2026-09-01T00:00:00Z" }),
  ];
  assert.deepEqual(upcomingReceivables(items, now).map(item => item.entryId), [1]);
});

test("próximos recebíveis ordena por data crescente e limita a 5", () => {
  const items = [
    ledgerItem({ entryId: 1, availableAt: "2026-09-20T00:00:00Z" }),
    ledgerItem({ entryId: 2, availableAt: "2026-09-05T00:00:00Z" }),
    ledgerItem({ entryId: 3, availableAt: "2026-09-12T00:00:00Z" }),
    ledgerItem({ entryId: 4, availableAt: "2026-09-06T00:00:00Z" }),
    ledgerItem({ entryId: 5, availableAt: "2026-09-30T00:00:00Z" }),
    ledgerItem({ entryId: 6, availableAt: "2026-09-04T00:00:00Z" }),
  ];
  assert.deepEqual(upcomingReceivables(items, now).map(item => item.entryId), [6, 2, 4, 3, 1]);
});

test("sem débito e KYC aprovado não gera alerta", () => {
  assert.deepEqual(dashboardAlerts(balance, kycApproved), []);
});

test("débito diferente de zero gera alerta de danger formatado em reais", () => {
  const alerts = dashboardAlerts({ ...balance, debt: -5000 }, kycApproved);
  assert.equal(alerts.length, 1);
  assert.equal(alerts[0].id, "debt");
  assert.equal(alerts[0].tone, "danger");
  assert.match(alerts[0].description, /-R\$\s*50,00/);
});

test("KYC recusado gera alerta danger com o link do provedor", () => {
  const kyc: KycView = { accountId: "acc-1", kycStatus: "REJECTED", providerUrl: "https://provider.example/kyc", requirements: [] };
  const alerts = dashboardAlerts(balance, kyc);
  assert.equal(alerts.length, 1);
  assert.equal(alerts[0].tone, "danger");
  assert.equal(alerts[0].actionUrl, "https://provider.example/kyc");
});

test("KYC pendente ou em análise gera alerta warning", () => {
  const pendente: KycView = { ...kycApproved, kycStatus: "PENDING" };
  const emAnalise: KycView = { ...kycApproved, kycStatus: "SUBMITTED" };
  assert.equal(dashboardAlerts(balance, pendente)[0].tone, "warning");
  assert.equal(dashboardAlerts(balance, emAnalise)[0].tone, "warning");
});

test("KYC recusado e débito juntos somam dois alertas", () => {
  const kyc: KycView = { ...kycApproved, kycStatus: "REJECTED" };
  const alerts = dashboardAlerts({ ...balance, debt: -100 }, kyc);
  assert.deepEqual(alerts.map(alert => alert.id), ["kyc", "debt"]);
});

test("fetchUpcomingReceivables combina busca e filtro", async () => {
  globalThis.fetch = (async () => new Response(JSON.stringify({
    items: [ledgerItem({ entryId: 9, availableAt: "2026-09-15T00:00:00Z" })],
    nextCursor: null,
  }), { status: 200, headers: { "content-type": "application/json" } })) as typeof fetch;

  const result = await fetchUpcomingReceivables(now);
  assert.deepEqual(result.map(item => item.entryId), [9]);
});
