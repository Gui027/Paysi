import assert from "node:assert/strict";
import { afterEach, test } from "node:test";
import { ApiRequestError } from "./api";
import { approveAffiliation, endAffiliation, formatCommissionBps, listAffiliateAffiliations, listMarketplace, listSellerAffiliations, parseCommissionPercent, requestAffiliation } from "./afiliados";

const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });

test("converte percentual textual para basis points sem ponto flutuante", () => {
  assert.equal(parseCommissionPercent("12,5"), 1250);
  assert.equal(parseCommissionPercent("50"), 5000);
  assert.equal(parseCommissionPercent("50,01"), null);
  assert.equal(parseCommissionPercent("-1"), null);
  assert.equal(parseCommissionPercent("10,999"), null);
  assert.equal(formatCommissionBps(1250), "12,5%");
});

test("lista somente a visão do vendedor e preserva paginação", async () => {
  let requestedUrl = "";
  globalThis.fetch = (async input => {
    requestedUrl = String(input);
    return new Response(JSON.stringify({ items: [], nextCursor: "next" }), {
      status: 200,
      headers: { "content-type": "application/json" },
    });
  }) as typeof fetch;

  const page = await listSellerAffiliations("current");
  assert.equal(page.nextCursor, "next");
  assert.match(requestedUrl, /role=SELLER/);
  assert.match(requestedUrl, /cursor=current/);
});

test("aprova enviando a comissão e recorrência definidas", async () => {
  let body = "";
  globalThis.fetch = (async (_input, init) => {
    body = String(init?.body);
    return new Response(JSON.stringify({ id: "affiliation", status: "APPROVED" }), {
      status: 200,
      headers: { "content-type": "application/json" },
    });
  }) as typeof fetch;

  await approveAffiliation("affiliation", 1750, "ALL_CYCLES");
  assert.deepEqual(JSON.parse(body), { commissionBps: 1750, recurrence: "ALL_CYCLES" });
});

test("encerra por fraude somente com motivo explícito", async () => {
  let body = "";
  globalThis.fetch = (async (_input, init) => {
    body = String(init?.body);
    return new Response(JSON.stringify({ id: "affiliation", status: "FRAUD_ENDED" }), {
      status: 200,
      headers: { "content-type": "application/json" },
    });
  }) as typeof fetch;

  await endAffiliation("affiliation", "FRAUD");
  assert.deepEqual(JSON.parse(body), { reason: "FRAUD" });
});

test("preserva erro de autorização retornado pela API", async () => {
  globalThis.fetch = (async () => new Response(JSON.stringify({ code: "AFFILIATION_NOT_ACTIONABLE" }), {
    status: 404,
    headers: { "content-type": "application/json" },
  })) as typeof fetch;
  await assert.rejects(() => approveAffiliation("other-account", 1000, "FIRST_CHARGE"),
    (error: unknown) => error instanceof ApiRequestError && error.status === 404);
});

test("lista vínculos existentes na visão do afiliado", async () => {
  let requestedUrl = "";
  globalThis.fetch = (async input => {
    requestedUrl = String(input);
    return new Response(JSON.stringify({ items: [], nextCursor: null }), {
      status: 200,
      headers: { "content-type": "application/json" },
    });
  }) as typeof fetch;
  await listAffiliateAffiliations();
  assert.match(requestedUrl, /role=AFFILIATE/);
});

test("carrega somente o contrato público do marketplace com cursor", async () => {
  let requestedUrl = "";
  globalThis.fetch = (async input => {
    requestedUrl = String(input);
    return new Response(JSON.stringify({ items: [], nextCursor: null }), {
      status: 200,
      headers: { "content-type": "application/json" },
    });
  }) as typeof fetch;
  await listMarketplace("next page");
  assert.match(requestedUrl, /^\/api\/v1\/marketplace\?/);
  assert.match(requestedUrl, /cursor=next\+page/);
});

test("solicita afiliação somente com o productId", async () => {
  let body = "";
  globalThis.fetch = (async (_input, init) => {
    body = String(init?.body);
    return new Response(JSON.stringify({ id: "affiliation", status: "PENDING" }), {
      status: 201,
      headers: { "content-type": "application/json" },
    });
  }) as typeof fetch;
  await requestAffiliation("product");
  assert.deepEqual(JSON.parse(body), { productId: "product" });
});

test("não esconde conflito de solicitação repetida", async () => {
  globalThis.fetch = (async () => new Response(JSON.stringify({ code: "AFFILIATION_ALREADY_ACTIVE" }), {
    status: 409,
    headers: { "content-type": "application/json" },
  })) as typeof fetch;
  await assert.rejects(() => requestAffiliation("product"),
    (error: unknown) => error instanceof ApiRequestError && error.problem.code === "AFFILIATION_ALREADY_ACTIVE");
});
