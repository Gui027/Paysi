import assert from "node:assert/strict";
import { test } from "node:test";
import { cepValido, formatarCep, formatarTelefone } from "./mascaras.js";

test("formata CEP com hífen após o quinto dígito", () => {
  assert.equal(formatarCep("01310100"), "01310-100");
  assert.equal(formatarCep("0131"), "0131");
});

test("valida quantidade de dígitos do CEP", () => {
  assert.equal(cepValido("01310-100"), true);
  assert.equal(cepValido("0131"), false);
});

test("formata telefone com DDD progressivamente", () => {
  assert.equal(formatarTelefone("1"), "(1");
  assert.equal(formatarTelefone("11"), "(11");
  assert.equal(formatarTelefone("119999"), "(11) 9999");
  assert.equal(formatarTelefone("1133334444"), "(11) 3333-4444");
  assert.equal(formatarTelefone("11999998888"), "(11) 99999-8888");
});

test("telefone ignora caracteres e limita a onze dígitos", () => {
  assert.equal(formatarTelefone("(11) 99999-8888"), "(11) 99999-8888");
  assert.equal(formatarTelefone("11999998888777"), "(11) 99999-8888");
});
