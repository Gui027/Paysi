import assert from "node:assert/strict";
import { test } from "node:test";
import { documentoValido, formatarDocumento } from "./documento.js";

test("formata CPF progressivamente enquanto o usuário digita", () => {
  assert.equal(formatarDocumento("529", "PF"), "529");
  assert.equal(formatarDocumento("52998224725", "PF"), "529.982.247-25");
});

test("formata CNPJ progressivamente enquanto o usuário digita", () => {
  assert.equal(formatarDocumento("11222333000181", "PJ"), "11.222.333/0001-81");
});

test("valida dígito verificador de CPF", () => {
  assert.equal(documentoValido("529.982.247-25", "PF"), true);
  assert.equal(documentoValido("111.444.777-35", "PF"), true);
  assert.equal(documentoValido("529.982.247-26", "PF"), false);
  assert.equal(documentoValido("111.111.111-11", "PF"), false);
});

test("valida dígito verificador de CNPJ", () => {
  assert.equal(documentoValido("11.222.333/0001-81", "PJ"), true);
  assert.equal(documentoValido("11.222.333/0001-80", "PJ"), false);
  assert.equal(documentoValido("11.111.111/1111-11", "PJ"), false);
});

test("rejeita documento com quantidade errada de dígitos", () => {
  assert.equal(documentoValido("123", "PF"), false);
  assert.equal(documentoValido("123", "PJ"), false);
});
