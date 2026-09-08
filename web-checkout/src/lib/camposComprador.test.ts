import assert from "node:assert/strict";
import { test } from "node:test";
import { comoChaveCampo, rotuloCampo, validarCampo } from "./camposComprador.js";

test("ignora personType como campo de texto (é o seletor PF/PJ)", () => {
  assert.equal(comoChaveCampo("personType"), null);
  assert.equal(comoChaveCampo("name"), "name");
  assert.equal(comoChaveCampo("address.zipCode"), "address.zipCode");
  assert.equal(comoChaveCampo("campo-desconhecido"), null);
});

test("rotula documento como CPF ou CNPJ conforme o tipo de pessoa", () => {
  assert.equal(rotuloCampo("taxId", "PF"), "CPF");
  assert.equal(rotuloCampo("taxId", "PJ"), "CNPJ");
  assert.equal(rotuloCampo("name", "PF"), "Nome completo");
});

test("exige preenchimento de campos obrigatórios genéricos", () => {
  assert.match(validarCampo("name", "  ", "PF") ?? "", /obrigat|Informe/);
  assert.equal(validarCampo("name", "Marina Duarte", "PF"), undefined);
});

test("valida e-mail com formato básico", () => {
  assert.notEqual(validarCampo("email", "invalido", "PF"), undefined);
  assert.equal(validarCampo("email", "marina@exemplo.com", "PF"), undefined);
});

test("valida documento pelo dígito verificador, não só pela quantidade de dígitos", () => {
  assert.notEqual(validarCampo("taxId", "111.111.111-11", "PF"), undefined);
  assert.equal(validarCampo("taxId", "529.982.247-25", "PF"), undefined);
  assert.equal(validarCampo("taxId", "11.222.333/0001-81", "PJ"), undefined);
});

test("valida CEP e UF com regras próprias", () => {
  assert.notEqual(validarCampo("address.zipCode", "123", "PF"), undefined);
  assert.equal(validarCampo("address.zipCode", "01310-100", "PF"), undefined);
  assert.notEqual(validarCampo("address.state", "São Paulo", "PF"), undefined);
  assert.equal(validarCampo("address.state", "SP", "PF"), undefined);
});
