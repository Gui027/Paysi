import assert from "node:assert/strict";
import { test } from "node:test";
import { calcularTermosHash } from "./termos.js";

test("calcula hash estável e prefixado por sha256: para o mesmo texto vigente", async () => {
  const first = await calcularTermosHash("https://paysi.com.br/termos");
  const second = await calcularTermosHash("https://paysi.com.br/termos");
  assert.equal(first, second);
  assert.match(first, /^sha256:[0-9a-f]{64}$/);
});

test("muda o hash quando o texto vigente muda", async () => {
  const original = await calcularTermosHash("https://paysi.com.br/termos");
  const changed = await calcularTermosHash("https://paysi.com.br/termos-v2");
  assert.notEqual(original, changed);
});
