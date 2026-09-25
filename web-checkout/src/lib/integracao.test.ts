import assert from "node:assert/strict";
import test from "node:test";
import { lerParametrosIntegracao, montarUrlRetorno } from "./integracao.js";

test("lê ref, e-mail e nome da URL e ignora valores inválidos", () => {
  assert.deepEqual(lerParametrosIntegracao("?ref=user_123&email=maria%40exemplo.com&name=Maria%20Souza"),
    { reference: "user_123", email: "maria@exemplo.com", name: "Maria Souza" });
  assert.deepEqual(lerParametrosIntegracao("?email=nao-e-email&ref=%20%20"),
    { reference: null, email: null, name: null });
  assert.equal(lerParametrosIntegracao(`?ref=${"x".repeat(129)}`).reference, null);
  assert.equal(lerParametrosIntegracao(`?ref=${"x".repeat(128)}`).reference, "x".repeat(128));
  assert.deepEqual(lerParametrosIntegracao(""), { reference: null, email: null, name: null });
});

test("monta a URL de retorno só para https ou localhost, com ref e status", () => {
  assert.equal(montarUrlRetorno("https://app.exemplo.com/obrigado", "user_123"),
    "https://app.exemplo.com/obrigado?paysi_status=approved&ref=user_123");
  assert.equal(montarUrlRetorno("https://app.exemplo.com/obrigado?plano=pro", null),
    "https://app.exemplo.com/obrigado?plano=pro&paysi_status=approved");
  assert.equal(montarUrlRetorno("http://localhost:3000/ok", "a"), "http://localhost:3000/ok?paysi_status=approved&ref=a");
});

test("recusa retorno inseguro ou ausente", () => {
  for (const ruim of [null, undefined, "", "http://exemplo.com/ok", "javascript:alert(1)", "https://u:p@exemplo.com", "não é url"]) {
    assert.equal(montarUrlRetorno(ruim, "x"), null, String(ruim));
  }
});
