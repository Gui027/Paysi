import assert from "node:assert/strict";
import { afterEach, test } from "node:test";
import { ApiRequestError } from "./api";
import { archiveProduct, createProduct, getProduct, listProducts, Product, productMatchesFilters, updateProduct, validateProductInput } from "./produtos";

const product: Product = {
  id: "f58b466b-9f22-4e64-a944-d8508d1dd06e",
  name: "Curso de Vendas",
  description: "Produto de demonstração",
  segment: "DIGITAL",
  chargeType: "ONE_TIME",
  affiliationEnabled: true,
  status: "DRAFT",
  createdAt: "2026-09-02T12:00:00Z",
};

const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });

test("combina busca e filtros sem diferenciar maiúsculas", () => {
  assert.equal(productMatchesFilters(product, { query: "curso", status: "DRAFT", segment: "DIGITAL", chargeType: "ONE_TIME" }), true);
  assert.equal(productMatchesFilters(product, { query: "assinatura", status: "", segment: "", chargeType: "" }), false);
});

test("lista a próxima página usando o cursor da API", async () => {
  let requestedUrl = "";
  globalThis.fetch = (async (input) => {
    requestedUrl = String(input);
    return new Response(JSON.stringify({ items: [product], nextCursor: "next-page" }), { status: 200, headers: { "content-type": "application/json" } });
  }) as typeof fetch;

  const page = await listProducts("current-page");
  assert.equal(page.items[0].name, product.name);
  assert.equal(page.nextCursor, "next-page");
  assert.match(requestedUrl, /^\/api\/v1\/products\?/);
  assert.match(requestedUrl, /cursor=current-page/);
});

test("trata detalhe indisponível como erro 404 sem inferir proprietário", async () => {
  globalThis.fetch = (async () => new Response(JSON.stringify({ code: "PRODUCT_NOT_FOUND", message: "Produto não encontrado" }), { status: 404, headers: { "content-type": "application/json" } })) as typeof fetch;
  await assert.rejects(() => getProduct(product.id), (error: unknown) => error instanceof ApiRequestError && error.status === 404);
});

test("arquiva o produto pelo endpoint autenticado", async () => {
  let method = "";
  globalThis.fetch = (async (_input, init) => {
    method = init?.method ?? "GET";
    return new Response(null, { status: 204 });
  }) as typeof fetch;
  await archiveProduct(product.id);
  assert.equal(method, "DELETE");
});

test("valida nome e limites do formulário antes do envio", () => {
  assert.deepEqual(validateProductInput({ name: " ", description: "a".repeat(2001), segment: "SAAS", chargeType: "SUBSCRIPTION", affiliationEnabled: false }), {
    name: "Informe o nome do produto.",
    description: "Use no máximo 2.000 caracteres.",
  });
});

test("cria rascunho sem enviar status ou regra de KYC", async () => {
  let body = "";
  globalThis.fetch = (async (_input, init) => {
    body = String(init?.body);
    return new Response(JSON.stringify(product), { status: 201, headers: { "content-type": "application/json" } });
  }) as typeof fetch;
  await createProduct({ name: "  Curso de Vendas  ", description: " ", segment: "DIGITAL", chargeType: "ONE_TIME", affiliationEnabled: true });
  assert.deepEqual(JSON.parse(body), { name: "Curso de Vendas", description: null, segment: "DIGITAL", chargeType: "ONE_TIME", affiliationEnabled: true });
});

test("preserva o erro de imutabilidade retornado na edição", async () => {
  globalThis.fetch = (async () => new Response(JSON.stringify({ code: "PRODUCT_CONTRACT_IMMUTABLE", message: "Contrato imutável" }), { status: 409, headers: { "content-type": "application/json" } })) as typeof fetch;
  await assert.rejects(() => updateProduct(product.id, { name: product.name, description: product.description, segment: "SAAS", chargeType: "SUBSCRIPTION", affiliationEnabled: product.affiliationEnabled }),
    (error: unknown) => error instanceof ApiRequestError && error.status === 409 && error.problem.code === "PRODUCT_CONTRACT_IMMUTABLE");
});
