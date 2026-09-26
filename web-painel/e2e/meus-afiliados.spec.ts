import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { mockSessao, mockDashboardVazio } from "./fixtures";

const base = { seller: "Eu", recurrence: "FIRST_CHARGE", endedReason: null, approvedAt: null, endedAt: null };
const ATIVO = { ...base, id: "af_1", productId: "p1", product: "Curso A", affiliateId: "a1", affiliate: "Maria Afiliada", commissionBps: 3000, status: "APPROVED", approvedAt: "2026-09-10T12:00:00Z", createdAt: "2026-09-09T12:00:00Z" };
const PEND1 = { ...base, id: "af_2", productId: "p1", product: "Curso A", affiliateId: "a2", affiliate: "João Pendente", commissionBps: 0, status: "PENDING", createdAt: "2026-09-25T12:00:00Z" };
const PEND2 = { ...base, id: "af_3", productId: "p2", product: "Curso B", affiliateId: "a3", affiliate: "Ana Pendente", commissionBps: 0, status: "PENDING", createdAt: "2026-09-26T12:00:00Z" };
const RECUSADO = { ...base, id: "af_4", productId: "p1", product: "Curso A", affiliateId: "a4", affiliate: "Pedro Recusado", commissionBps: 0, status: "ENDED", endedReason: "BY_SELLER", endedAt: "2026-09-20T12:00:00Z", createdAt: "2026-09-19T12:00:00Z" };
const PROGRAMA = { productId: "p1", commissionBps: 2500, recurrence: "ALL_CYCLES", autoApprove: false, supportEmail: null, description: null };

async function preparar(page: import("@playwright/test").Page) {
  await mockSessao(page);
  await mockDashboardVazio(page);
  await page.route("**/api/v1/affiliations?**", (route) => route.fulfill({ json: { items: [ATIVO, PEND1, PEND2, RECUSADO], nextCursor: null } }));
  await page.route("**/api/v1/products/*/affiliate-program", (route) => route.fulfill({ json: PROGRAMA }));
}

test.describe("meus afiliados (vendedor)", () => {
  test("mostra as três abas com contagem e filtra por produto e busca", async ({ page }) => {
    await preparar(page);
    await page.goto("/afiliados");
    await expect(page.getByRole("heading", { name: "Meus afiliados" })).toBeVisible();
    await expect(page.getByRole("tab", { name: /Solicitações pendentes \(2\)/ })).toBeVisible();
    await expect(page.getByRole("button", { name: "Maria Afiliada" })).toBeVisible();
    await expect(page.getByRole("cell", { name: "30%" })).toBeVisible();

    await page.getByRole("tab", { name: /Solicitações pendentes/ }).click();
    await expect(page.getByRole("button", { name: "João Pendente" })).toBeVisible();
    await page.getByRole("combobox").selectOption("p2");
    await expect(page.getByRole("button", { name: "João Pendente" })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Ana Pendente" })).toBeVisible();

    await page.getByRole("combobox").selectOption("");
    await page.getByRole("tab", { name: /Recusados/ }).click();
    await expect(page.getByRole("cell", { name: "Recusado", exact: true })).toBeVisible();
  });

  test("aceita várias solicitações de uma vez com a comissão do programa do produto", async ({ page }) => {
    await preparar(page);
    const aprovacoes: Record<string, unknown>[] = [];
    await page.route("**/api/v1/affiliations/*/approve", async (route) => {
      const corpo = route.request().postDataJSON();
      const id = route.request().url().split("/").at(-2)!;
      aprovacoes.push({ id, ...corpo });
      const original = id === "af_2" ? PEND1 : PEND2;
      await route.fulfill({ json: { ...original, status: "APPROVED", commissionBps: corpo.commissionBps, recurrence: corpo.recurrence, approvedAt: "2026-09-26T12:00:00Z" } });
    });
    await page.goto("/afiliados");
    await page.getByRole("tab", { name: /Solicitações pendentes/ }).click();
    await page.getByLabel("Selecionar todos").check();
    await page.getByRole("button", { name: /^Ações/ }).click();
    await page.getByRole("menuitem", { name: "Aceitar" }).click();
    await page.getByLabel("Comissão (%)").fill("20");
    await page.getByRole("button", { name: "Confirmar" }).click();
    await expect(page.getByText(/2 afiliações aprovadas/)).toBeVisible();
    expect(aprovacoes).toHaveLength(2);
    expect(aprovacoes[0]).toMatchObject({ commissionBps: 2000 });
    await page.getByRole("tab", { name: /Ativos/ }).click();
    await expect(page.getByRole("button", { name: "João Pendente" })).toBeVisible();
  });

  test("sugere a comissão do programa do produto ao aceitar uma solicitação", async ({ page }) => {
    await preparar(page);
    await page.goto("/afiliados");
    await page.getByRole("tab", { name: /Solicitações pendentes/ }).click();
    await page.getByLabel("Selecionar João Pendente").check();
    await page.getByRole("button", { name: /^Ações/ }).click();
    await page.getByRole("menuitem", { name: "Aceitar" }).click();
    await expect(page.getByLabel("Comissão (%)")).toHaveValue("25");
    await expect(page.getByLabel("Recorrência")).toHaveValue("ALL_CYCLES");
  });

  test("recusa uma solicitação", async ({ page }) => {
    await preparar(page);
    let motivo: unknown = null;
    await page.route("**/api/v1/affiliations/af_2/end", async (route) => {
      motivo = route.request().postDataJSON();
      await route.fulfill({ json: { ...PEND1, status: "ENDED", endedReason: "BY_SELLER", endedAt: "2026-09-26T12:00:00Z" } });
    });
    await page.goto("/afiliados");
    await page.getByRole("tab", { name: /Solicitações pendentes/ }).click();
    await page.getByLabel("Selecionar João Pendente").check();
    await page.getByRole("button", { name: /^Ações/ }).click();
    await page.getByRole("menuitem", { name: "Recusar" }).click();
    await page.getByRole("button", { name: "Confirmar" }).click();
    await expect(page.getByText("Solicitação recusada.")).toBeVisible();
    expect(motivo).toMatchObject({ reason: "BY_SELLER" });
  });

  test("passa no axe nas três abas", async ({ page }) => {
    await preparar(page);
    await page.goto("/afiliados");
    for (const nome of [/Ativos/, /Solicitações pendentes/, /Recusados/]) {
      await page.getByRole("tab", { name: nome }).click();
      const axe = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa"]).analyze();
      expect(axe.violations.filter(v => v.impact === "critical" || v.impact === "serious"), String(nome)).toEqual([]);
    }
  });
});
