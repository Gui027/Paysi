import assert from "node:assert/strict";
import test from "node:test";
import { allEventKeys, emptyLogsQuery, eventCatalog, eventLabel, isValidWebhookUrl, keysFromTypes, prettyBody, typesFromKeys } from "./webhooks";

test("Reembolso liga os dois tipos de reembolso e volta como uma opção só", () => {
  assert.deepEqual(typesFromKeys(["refund"]), ["PAYMENT.REFUNDED", "PAYMENT.PARTIALLY_REFUNDED"]);
  assert.deepEqual(keysFromTypes(["payment.refunded"]), ["refund"]);
  assert.deepEqual(keysFromTypes(["PAYMENT.APPROVED", "PIX.GENERATED"]).sort(), ["approved", "pix"]);
  assert.equal(allEventKeys.length, eventCatalog.length);
});

test("rótulos dos eventos, inclusive o de teste e os desconhecidos", () => {
  assert.equal(eventLabel("PAYMENT.APPROVED"), "Compra aprovada");
  assert.equal(eventLabel("payment.refunded"), "Reembolso");
  assert.equal(eventLabel("WEBHOOK.TEST"), "Teste");
  assert.equal(eventLabel("OUTRO.EVENTO"), "OUTRO.EVENTO");
});

test("a URL do webhook precisa ser HTTPS público e sem usuário", () => {
  assert.equal(isValidWebhookUrl("https://api.exemplo.com/hooks?id=1"), true);
  assert.equal(isValidWebhookUrl("http://api.exemplo.com"), false);
  assert.equal(isValidWebhookUrl("https://localhost/x"), false);
  assert.equal(isValidWebhookUrl("https://user:pw@api.exemplo.com"), false);
  assert.equal(isValidWebhookUrl("texto"), false);
});

test("o corpo é indentado quando é JSON e fica como veio quando não é", () => {
  assert.equal(prettyBody('{"a":1}'), '{\n  "a": 1\n}');
  assert.equal(prettyBody("<html>"), "<html>");
  assert.equal(prettyBody(null), "");
  assert.equal(emptyLogsQuery.page, 1);
});
