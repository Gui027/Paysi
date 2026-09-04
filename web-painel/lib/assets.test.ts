import assert from "node:assert/strict";
import { afterEach, test } from "node:test";
import { ApiRequestError } from "./api";
import { assetContentUrl, removeAsset, uploadAsset } from "./assets";

const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });

test("monta a url pública de conteúdo a partir do id do ativo", () => {
  assert.equal(assetContentUrl("abc-123"), "/api/v1/assets/abc-123/content");
});

test("envia kind e arquivo como multipart mantendo os cookies de sessão", async () => {
  let requestedUrl = "";
  let requestedInit: RequestInit | undefined;
  globalThis.fetch = (async (input, init) => {
    requestedUrl = String(input);
    requestedInit = init;
    return new Response(JSON.stringify({ id: "asset-1", kind: "LOGO", contentType: "image/png", byteSize: 10, width: 1, height: 1, url: "http://backend/v1/assets/asset-1/content" }),
      { status: 201, headers: { "content-type": "application/json" } });
  }) as typeof fetch;

  const file = new File(["conteudo"], "logo.png", { type: "image/png" });
  const asset = await uploadAsset("LOGO", file);

  assert.equal(requestedUrl, "/api/v1/assets");
  assert.equal(requestedInit?.method, "POST");
  assert.equal(requestedInit?.credentials, "include");
  const body = requestedInit?.body as FormData;
  assert.equal(body.get("kind"), "LOGO");
  assert.equal(body.get("file"), file);
  assert.equal(asset.id, "asset-1");
});

test("propaga erro de imagem inválida com o campo correto", async () => {
  globalThis.fetch = (async () => new Response(JSON.stringify({ code: "ASSET_INVALID", message: "Use uma imagem PNG ou JPEG válida", field: "file" }), { status: 400, headers: { "content-type": "application/json" } })) as typeof fetch;

  await assert.rejects(() => uploadAsset("BANNER", new File(["x"], "x.gif", { type: "image/gif" })),
    (error: unknown) => error instanceof ApiRequestError && error.status === 400 && error.problem.field === "file");
});

test("remove o ativo pelo endpoint autenticado", async () => {
  let method = "";
  globalThis.fetch = (async (_input, init) => {
    method = init?.method ?? "GET";
    return new Response(null, { status: 204 });
  }) as typeof fetch;

  await removeAsset("asset-1");
  assert.equal(method, "DELETE");
});
