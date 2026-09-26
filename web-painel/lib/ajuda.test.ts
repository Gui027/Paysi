import assert from "node:assert/strict";
import test from "node:test";
import { filterCategories, findCategory, helpCategories } from "./ajuda";

test("a central tem as nove categorias", () => {
  assert.equal(helpCategories.length, 9);
  assert.equal(new Set(helpCategories.map(item => item.slug)).size, 9);
});

test("a busca ignora acentos e maiúsculas", () => {
  assert.deepEqual(filterCategories("INTEGRACOES").map(item => item.slug), ["integracoes"]);
  assert.equal(filterCategories("  ").length, 9);
  assert.equal(filterCategories("zzz").length, 0);
  assert.equal(findCategory("financeiro")?.title, "Financeiro");
  assert.equal(findCategory("x"), undefined);
});
