import assert from "node:assert/strict";
import { afterEach, test } from "node:test";
import { consultarCep } from "./cep.js";

const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });

test("consulta CEP e normaliza os dados usados no endereço", async () => {
  globalThis.fetch = async () => new Response(JSON.stringify({
    logradouro: "Avenida Paulista",
    bairro: "Bela Vista",
    localidade: "São Paulo",
    uf: "SP",
  }));

  assert.deepEqual(await consultarCep("01310-100"), {
    street: "Avenida Paulista",
    district: "Bela Vista",
    city: "São Paulo",
    state: "SP",
  });
});

test("recusa CEP inexistente ou incompleto", async () => {
  await assert.rejects(() => consultarCep("123"), /CEP_INVALIDO/);
  globalThis.fetch = async () => new Response(JSON.stringify({ erro: true }));
  await assert.rejects(() => consultarCep("00000000"), /CEP_NAO_ENCONTRADO/);
});
