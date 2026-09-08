import assert from "node:assert/strict";
import { test } from "node:test";
import { cepValido, formatarCep } from "./mascaras.js";

test("formata CEP com hífen após o quinto dígito", () => {
  assert.equal(formatarCep("01310100"), "01310-100");
  assert.equal(formatarCep("0131"), "0131");
});

test("valida quantidade de dígitos do CEP", () => {
  assert.equal(cepValido("01310-100"), true);
  assert.equal(cepValido("0131"), false);
});
