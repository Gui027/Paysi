import assert from "node:assert/strict";
import { test } from "node:test";
import { formatarCentavos } from "./moeda";

test("formata zero como R$ 0,00", () => {
  assert.match(formatarCentavos(0), /^R\$\s*0,00$/);
});

test("formata centavos como valor positivo em reais", () => {
  assert.match(formatarCentavos(1050), /^R\$\s*10,50$/);
});

test("formata valores negativos (dívida) com o sinal antes do R$", () => {
  assert.match(formatarCentavos(-500), /^-R\$\s*5,00$/);
});

test("usa separador de milhar em valores grandes", () => {
  assert.match(formatarCentavos(123456789), /^R\$\s*1\.234\.567,89$/);
});
