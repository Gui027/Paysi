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

test("não duplica escopos e lista as doze permissões", () => {
  assert.deepEqual(toggleScope(["finance"], "finance", true), ["finance"]);
  assert.equal(allScopes.length, 12);
});

test("criar pagamentos depende da leitura de pagamentos", () => {
  assert.deepEqual(toggleScope([], "payments_write", true).sort(), ["payments_read", "payments_write"]);
  assert.deepEqual(toggleScope(["payments_read", "payments_write"], "payments_read", false), []);
});

test("gerenciar assinaturas depende da leitura de assinaturas", () => {
  assert.deepEqual(toggleScope([], "subscriptions_write", true).sort(), ["subscriptions_read", "subscriptions_write"]);
  assert.deepEqual(toggleScope(["subscriptions_read", "subscriptions_write"], "subscriptions_read", false), []);
});

test("gerenciar catálogo depende da leitura de produtos", () => {
  assert.deepEqual(toggleScope([], "products_write", true).sort(), ["products", "products_write"]);
  assert.deepEqual(toggleScope(["products", "products_write"], "products", false), []);
});
