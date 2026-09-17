import assert from "node:assert/strict";
import { test } from "node:test";
import {
  cvvValido,
  formatarNumeroCartao,
  formatarValidade,
  gerarTokenCartao,
  numeroCartaoValido,
  validadeValida,
} from "./cartao.js";

test("formata número do cartão em blocos de 4 dígitos", () => {
  assert.equal(formatarNumeroCartao("4111111111111111"), "4111 1111 1111 1111");
  assert.equal(formatarNumeroCartao("4111"), "4111");
});

test("formata validade como MM/AA", () => {
  assert.equal(formatarValidade("1228"), "12/28");
  assert.equal(formatarValidade("12"), "12");
});

test("valida número de cartão pelo dígito verificador de Luhn", () => {
  assert.equal(numeroCartaoValido("4111 1111 1111 1111"), true);
  assert.equal(numeroCartaoValido("4111 1111 1111 1112"), false);
  assert.equal(numeroCartaoValido("123"), false);
});

test("recusa validade vencida e aceita validade dentro do mês corrente ou futura", () => {
  const referencia = new Date(2026, 5, 15); // 15 de junho de 2026
  assert.equal(validadeValida("0426", referencia), false); // venceu em abril
  assert.equal(validadeValida("0526", referencia), false); // venceu no fim de maio
  assert.equal(validadeValida("0626", referencia), true); // ainda dentro de junho
  assert.equal(validadeValida("0726", referencia), true); // julho, no futuro
  assert.equal(validadeValida("13", referencia), false); // mês inválido
});

test("aceita CVV de 3 ou 4 dígitos, recusa o resto", () => {
  assert.equal(cvvValido("123"), true);
  assert.equal(cvvValido("1234"), true);
  assert.equal(cvvValido("12"), false);
});

test("gera token opaco sem nenhum dígito do cartão", () => {
  const token = gerarTokenCartao();
  assert.match(token, /^tok_[0-9a-f-]{36}$/);
});
