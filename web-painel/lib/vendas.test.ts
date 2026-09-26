import assert from "node:assert/strict";
import test from "node:test";
import {
  emptySalesQuery,
  formatDocumento,
  formatTelefone,
  paginasVisiveis,
  salesParams,
  whatsappUrl,
} from "./vendas";

test("monta a query da lista com aba, busca, filtros e página", () => {
  const params = salesParams({ ...emptySalesQuery, tab: "all", q: "  maria ", statuses: ["PAID", "REFUNDED"], method: "PIX", productId: "p1", from: "2026-09-01", to: "2026-09-30", page: 3 });
  assert.equal(params.get("tab"), "all");
  assert.equal(params.get("q"), "maria");
  assert.deepEqual(params.getAll("status"), ["PAID", "REFUNDED"]);
  assert.equal(params.get("method"), "PIX");
  assert.equal(params.get("productId"), "p1");
  assert.equal(params.get("page"), "3");
  assert.equal(salesParams(emptySalesQuery, false).has("page"), false);
  assert.equal(salesParams(emptySalesQuery).has("q"), false);
});

test("formata CPF, CNPJ e celular só com texto", () => {
  assert.equal(formatDocumento("16573709764"), "165.737.097-64");
  assert.equal(formatDocumento("11222333000181"), "11.222.333/0001-81");
  assert.equal(formatTelefone("27999513505"), "+55 27 99951-3505");
  assert.equal(formatTelefone("5527999513505"), "+55 27 99951-3505");
  assert.equal(formatTelefone("2733334444"), "+55 27 3333-4444");
  assert.equal(formatTelefone(null), "");
});

test("link do WhatsApp aceita número com ou sem 55 e recusa número curto", () => {
  assert.equal(whatsappUrl("27999513505"), "https://wa.me/5527999513505");
  assert.equal(whatsappUrl("5527999513505"), "https://wa.me/5527999513505");
  assert.equal(whatsappUrl("123"), null);
  assert.equal(whatsappUrl(null), null);
});

test("paginação numerada com reticências", () => {
  assert.deepEqual(paginasVisiveis(1, 5), [1, 2, 3, 4, 5]);
  assert.deepEqual(paginasVisiveis(1, 33), [1, 2, 3, 4, 5, "…", 33]);
  assert.deepEqual(paginasVisiveis(17, 33), [1, "…", 16, 17, 18, "…", 33]);
  assert.deepEqual(paginasVisiveis(33, 33), [1, "…", 29, 30, 31, 32, 33]);
  assert.deepEqual(paginasVisiveis(1, 1), [1]);
});
