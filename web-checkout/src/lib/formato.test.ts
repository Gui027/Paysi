import assert from "node:assert/strict";
import { test } from "node:test";
import { lerCentavos } from "./formato.js";

test("converte o valor escolhido pelo comprador em centavos", () => {
  assert.equal(lerCentavos("5,00"), 500);
  assert.equal(lerCentavos("49,90"), 4990);
  assert.equal(lerCentavos("1.234,56"), 123456);
  assert.equal(lerCentavos("valor inválido"), null);
});
