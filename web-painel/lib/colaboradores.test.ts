import assert from "node:assert/strict";
import test from "node:test";
import { areaOrder, hasFullAccess, isValidEmail, permissionsPayload } from "./colaboradores";

test("acesso total vira ALL e sem ele vão as áreas marcadas", () => {
  assert.deepEqual(permissionsPayload(true, ["sales"]), ["ALL"]);
  assert.deepEqual(permissionsPayload(false, ["sales", "finance"]), ["sales", "finance"]);
  assert.equal(hasFullAccess({ permissions: ["ALL"] }), true);
  assert.equal(hasFullAccess({ permissions: ["sales"] }), false);
});

test("valida o e-mail e lista as sete áreas", () => {
  assert.equal(isValidEmail(" ana@exemplo.com "), true);
  assert.equal(isValidEmail("ana@exemplo"), false);
  assert.equal(areaOrder.length, 7);
});
