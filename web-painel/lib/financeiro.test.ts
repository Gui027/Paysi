import assert from "node:assert/strict";
import test from "node:test";
import { ApiRequestError } from "./api";
import { feeMethodLabel, financeError, formatPixKey, kycTitle, maskCnpj, payoutStatusLabel } from "./financeiro";

test("mascara o CNPJ enquanto digita", () => {
  assert.equal(maskCnpj("11"), "11");
  assert.equal(maskCnpj("11222"), "11.222");
  assert.equal(maskCnpj("11222333"), "11.222.333");
  assert.equal(maskCnpj("112223330001"), "11.222.333/0001");
  assert.equal(maskCnpj("11222333000181"), "11.222.333/0001-81");
  assert.equal(maskCnpj("11.222.333/0001-81999"), "11.222.333/0001-81");
});

test("chave Pix aparece formatada só quando é CPF ou CNPJ", () => {
  assert.equal(formatPixKey("16703691703", "CPF"), "167.036.917-03");
  assert.equal(formatPixKey("11222333000181", "CNPJ"), "11.222.333/0001-81");
  assert.equal(formatPixKey("maria@exemplo.com", "EMAIL"), "maria@exemplo.com");
  assert.equal(formatPixKey(null, "CPF"), "");
});

test("rótulos de saque, taxas e verificação", () => {
  assert.equal(payoutStatusLabel.SENT, "Processando");
  assert.equal(payoutStatusLabel.CONFIRMED, "Sucesso");
  assert.equal(feeMethodLabel.CARD_6, "Cartão de crédito em 2 a 6x");
  assert.equal(kycTitle.APPROVED, "Identidade verificada");
});

test("mensagens de erro conhecidas do financeiro", () => {
  assert.equal(financeError(new ApiRequestError(403, { code: "MFA_REQUIRED" }), "x"), "Ative a verificação em duas etapas para continuar.");
  assert.equal(financeError(new ApiRequestError(409, { code: "TAX_ID_IN_USE" }), "x"), "Este CNPJ já está cadastrado na Paysi.");
  assert.equal(financeError(new ApiRequestError(422, { message: "Chave inválida" }), "x"), "Chave inválida");
  assert.equal(financeError(new Error("rede"), "Falhou"), "Falhou");
});
