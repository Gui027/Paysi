import assert from "node:assert/strict";
import test from "node:test";
import { allScopes, toggleScope } from "./apikeys";

test("marcar Reembolsar vendas marca Vendas junto", () => {
  assert.deepEqual(toggleScope([], "sales_refund", true).sort(), ["sales", "sales_refund"]);
});

test("desmarcar Vendas desmarca Reembolsar vendas", () => {
  assert.deepEqual(toggleScope(["sales", "sales_refund", "finance"], "sales", false), ["finance"]);
  assert.deepEqual(toggleScope(["sales", "sales_refund"], "sales_refund", false), ["sales"]);
});

test("não duplica escopos e lista os sete endpoints", () => {
  assert.deepEqual(toggleScope(["finance"], "finance", true), ["finance"]);
  assert.equal(allScopes.length, 7);
});
