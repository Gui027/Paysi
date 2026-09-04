import assert from "node:assert/strict";
import { afterEach, test } from "node:test";
import { ApiRequestError } from "./api";
import { Appearance, AppearanceInput, getAppearance, updateAppearance, validateAppearanceInput } from "./aparencia";

const input: AppearanceInput = {
  logoAssetId: "f58b466b-9f22-4e64-a944-d8508d1dd06e",
  bannerAssetId: null,
  sideImageAssetId: null,
  primaryColor: "#2563EB",
  buttonText: "Comprar agora",
};

const appearance: Appearance = { ...input, updatedAt: "2026-09-02T12:00:00Z" };

const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });

test("rejeita cor fora do padrão hexadecimal", () => {
  assert.deepEqual(validateAppearanceInput({ ...input, primaryColor: "azul" }), {
    primaryColor: "Use uma cor hexadecimal no formato #RRGGBB.",
  });
});

test("exige texto do botão e limita a 40 caracteres", () => {
  assert.deepEqual(validateAppearanceInput({ ...input, buttonText: "  " }), {
    buttonText: "Informe o texto do botão.",
  });
  assert.deepEqual(validateAppearanceInput({ ...input, buttonText: "a".repeat(41) }), {
    buttonText: "Use no máximo 40 caracteres.",
  });
});

test("aceita entrada válida sem erros", () => {
  assert.deepEqual(validateAppearanceInput(input), {});
});

test("carrega a aparência da oferta autenticada", async () => {
  let requestedUrl = "";
  globalThis.fetch = (async request => {
    requestedUrl = String(request);
    return new Response(JSON.stringify(appearance), { status: 200, headers: { "content-type": "application/json" } });
  }) as typeof fetch;

  const loaded = await getAppearance("offer-1");
  assert.equal(loaded.primaryColor, "#2563EB");
  assert.equal(requestedUrl, "/api/v1/offers/offer-1/appearance");
});

test("envia o texto do botão já normalizado ao salvar", async () => {
  let body = "";
  globalThis.fetch = (async (_request, init) => {
    body = String(init?.body);
    return new Response(JSON.stringify(appearance), { status: 200, headers: { "content-type": "application/json" } });
  }) as typeof fetch;

  await updateAppearance("offer-1", { ...input, buttonText: "  Comprar agora  " });
  assert.deepEqual(JSON.parse(body), input);
});

test("propaga erro de ativo incompatível", async () => {
  globalThis.fetch = (async () => new Response(JSON.stringify({ code: "APPEARANCE_INVALID", message: "Tipo de ativo incompatível", field: "logoAssetId" }), { status: 400, headers: { "content-type": "application/json" } })) as typeof fetch;
  await assert.rejects(() => updateAppearance("offer-1", input),
    (error: unknown) => error instanceof ApiRequestError && error.status === 400 && error.problem.field === "logoAssetId");
});
