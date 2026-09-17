import assert from "node:assert/strict";
import { test } from "node:test";
import { statusFinalFalhou, statusFinalPago } from "./statusCobranca.js";

test("classifica status finais de pagamento e falha", () => {
  assert.equal(statusFinalPago("PAID"), true);
  assert.equal(statusFinalPago("approved"), true);
  assert.equal(statusFinalPago("PENDING"), false);
  assert.equal(statusFinalFalhou("FAILED"), true);
  assert.equal(statusFinalFalhou("EXPIRED"), true);
  assert.equal(statusFinalFalhou("declined"), true);
  assert.equal(statusFinalFalhou("PENDING"), false);
});
