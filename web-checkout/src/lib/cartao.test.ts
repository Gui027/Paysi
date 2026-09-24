import assert from "node:assert/strict";
import { test } from "node:test";
import {
  cvvValido,
  formatarNumeroCartao,
  formatarValidade,
  cartaoCompleto,
  cepValido,
  formatarCep,
  montarDadosCartao,
  telefoneValido,
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

const digitado = {
  nome: "  Ana Souza ",
  numero: "4111 1111 1111 1111",
  validade: "12/30",
  cvv: "123",
  cep: "01310-100",
  numeroEndereco: "10",
  telefone: "(11) 99999-8888",
};

test("valida e formata CEP e telefone", () => {
  assert.equal(formatarCep("01310100"), "01310-100");
  assert.equal(cepValido("01310-100"), true);
  assert.equal(cepValido("0131"), false);
  assert.equal(telefoneValido("(11) 99999-8888"), true);
  assert.equal(telefoneValido("1133334444"), true);
  assert.equal(telefoneValido("123"), false);
});

test("cartão só está completo com endereço e telefone do titular (exigidos pelo provedor)", () => {
  const agora = new Date(2026, 5, 15);
  assert.equal(cartaoCompleto(digitado, agora), true);
  assert.equal(cartaoCompleto({ ...digitado, cep: "" }, agora), false);
  assert.equal(cartaoCompleto({ ...digitado, telefone: "12" }, agora), false);
  assert.equal(cartaoCompleto({ ...digitado, numeroEndereco: " " }, agora), false);
});

test("monta os dados do cartão só com dígitos e validade em mês e ano de 4 dígitos", () => {
  assert.deepEqual(montarDadosCartao(digitado), {
    holderName: "Ana Souza",
    number: "4111111111111111",
    expiryMonth: "12",
    expiryYear: "2030",
    ccv: "123",
    postalCode: "01310100",
    addressNumber: "10",
    phone: "11999998888",
  });
  assert.equal(montarDadosCartao({ ...digitado, validade: "03/29" }).expiryMonth, "3");
});
