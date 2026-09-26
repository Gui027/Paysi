import assert from "node:assert/strict";
import test from "node:test";
import { formatCell, formatDay, hubItems, monthGrid, presetRange, reportParams, toIso, whatsappLink, emptyReportQuery } from "./relatorios";

test("os períodos relativos partem de hoje", () => {
  const today = new Date(2026, 8, 26);
  assert.deepEqual(presetRange("today", today), { from: "2026-09-26", to: "2026-09-26" });
  assert.deepEqual(presetRange("7d", today), { from: "2026-09-20", to: "2026-09-26" });
  assert.deepEqual(presetRange("30d", today), { from: "2026-08-28", to: "2026-09-26" });
  assert.deepEqual(presetRange("next30", today), { from: "2026-09-26", to: "2026-10-26" });
  assert.deepEqual(presetRange("all", today), { from: "", to: "" });
  assert.equal(toIso(new Date(2026, 0, 5)), "2026-01-05");
});

test("o calendário começa na segunda e completa as semanas", () => {
  const weeks = monthGrid(2026, 8);
  assert.equal(weeks[0][0], null);
  assert.equal(weeks[0][1], "2026-09-01");
  assert.equal(weeks[weeks.length - 1].length, 7);
  assert.equal(weeks.flat().filter(Boolean).length, 30);
});

test("as células saem formatadas por tipo", () => {
  assert.match(formatCell({ key: "v", label: "V", type: "money" }, 2146), /21,46/);
  assert.equal(formatCell({ key: "d", label: "D", type: "date" }, "2026-10-09"), "09/10/2026");
  assert.equal(formatCell({ key: "t", label: "T", type: "text" }, ""), "");
  assert.equal(formatDay("texto"), "texto");
});

test("os parâmetros só levam o que foi preenchido", () => {
  assert.equal(reportParams(emptyReportQuery, false), "");
  assert.equal(reportParams({ ...emptyReportQuery, q: " ana ", tab: "card", page: 2 }, true), "q=ana&tab=card&page=2");
});

test("o link do WhatsApp exige telefone e prefixa o Brasil", () => {
  assert.equal(whatsappLink("", "Ana", "Curso"), null);
  const link = whatsappLink("(11) 99999-8888", "Ana Maria", "Curso");
  assert.ok(link?.startsWith("https://wa.me/5511999998888?text="));
  assert.ok(decodeURIComponent(link!).includes("Olá, Ana!"));
});

test("o menu de relatórios tem os nove relatórios", () => {
  assert.equal(hubItems.length, 9);
});
