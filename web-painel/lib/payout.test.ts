import assert from "node:assert/strict";
import { afterEach, test } from "node:test";
import { ApiRequestError } from "./api";
import { BankAccountInput, createBankAccount, createMfaChallenge, maskTaxId, parsePayoutAmount, payoutError, requestPayout, validateBankAccount, verifyMfaChallenge } from "./payout";

const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });

const bank: BankAccountInput = { holderType: "PF", holderTaxId: "529.982.247-25", holderName: "Guilherme Rodrigues", bankCode: "001", branch: "1234", accountNumber: "12345678", digit: "9", accountType: "CHECKING", pixKeyType: "CPF", pixKey: "52998224725" };

test("mascara documento sem ultrapassar o tamanho do tipo de titular", () => {
  assert.equal(maskTaxId("52998224725", "PF"), "529.982.247-25");
  assert.equal(maskTaxId("12345678000199", "PJ"), "12.345.678/0001-99");
});
test("valida campos bancários antes de iniciar o MFA", () => {
  assert.deepEqual(validateBankAccount(bank), {});
  const errors = validateBankAccount({ ...bank, holderTaxId: "123", bankCode: "1", pixKey: "" });
  assert.ok(errors.holderTaxId);
  assert.ok(errors.bankCode);
  assert.ok(errors.pixKey);
});

test("converte valor digitado em centavos sem arredondamento decimal", () => {
  assert.equal(parsePayoutAmount("1.234,56"), 123456);
  assert.equal(parsePayoutAmount("200"), 20000);
  assert.equal(parsePayoutAmount("10,999"), null);
});

test("cria e verifica desafio MFA para a operação informada", async () => {
  const urls: string[] = [];
  globalThis.fetch = (async input => {
    urls.push(String(input));
    return new Response(JSON.stringify({ challengeId: "challenge-1", operation: "PAYOUT", expiresAt: "2026-09-11T12:00:00Z", verified: urls.length > 1 }), { status: urls.length > 1 ? 200 : 201, headers: { "content-type": "application/json" } });
  }) as typeof fetch;
  await createMfaChallenge("PAYOUT");
  const verified = await verifyMfaChallenge("challenge-1", "123456");
  assert.equal(verified.verified, true);
  assert.deepEqual(urls, ["/api/v1/mfa/challenges", "/api/v1/mfa/challenges/challenge-1/verify"]);
});

test("normaliza dados bancários e envia o desafio verificado", async () => {
  let request: RequestInit | undefined;
  globalThis.fetch = (async (_input, init) => {
    request = init;
    return new Response(JSON.stringify({ id: "bank-1", bankCode: "001", branch: "1234", numberLast4: "5678", verifiedAt: "2026-09-11T12:00:00Z" }), { status: 201, headers: { "content-type": "application/json" } });
  }) as typeof fetch;
  await createBankAccount(bank, "challenge-1");
  assert.equal((request?.headers as Record<string, string>)["X-MFA-Challenge-Id"], "challenge-1");
  assert.equal(JSON.parse(String(request?.body)).holderTaxId, "52998224725");
});

test("mantém a mesma chave de idempotência no pedido de saque", async () => {
  let request: RequestInit | undefined;
  globalThis.fetch = (async (_input, init) => {
    request = init;
    return new Response(JSON.stringify({ payoutId: "payout-1", status: "SENT", receiptUrl: null, idempotentReplay: false }), { status: 201, headers: { "content-type": "application/json" } });
  }) as typeof fetch;
  await requestPayout(20000, "bank-1", "challenge-1", "same-operation-key");
  assert.equal((request?.headers as Record<string, string>)["Idempotency-Key"], "same-operation-key");
});

test("explica erros de titularidade e desafio expirado", () => {
  assert.match(payoutError(new ApiRequestError(422, { code: "BANK_HOLDER_MISMATCH" })), /mesmo titular/);
  assert.match(payoutError(new ApiRequestError(403, { code: "MFA_CHALLENGE_INVALID" })), /expirou/);
});
