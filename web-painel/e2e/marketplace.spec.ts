import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { mockSessao, mockDashboardVazio, SESSAO_VENDEDOR } from "./fixtures";

const ITEM = {
  productId: "prod_mk_1", product: "Combo Milionário", description: "Aprenda a vender todos os dias.", seller: "NT Serviços",
  segment: "DIGITAL", chargeType: "ONE_TIME", startingPriceCents: 9700, suggestedCommissionBps: 5000, guaranteeDays: 7,
  payoutDelayDays: 32, attributionDays: 60, maxCommissionCents: 4850,
};
const OUTRO = { ...ITEM, productId: "prod_mk_2", product: "Robô Studio", seller: "Giovanni", segment: "SAAS", chargeType: "SUBSCRIPTION", maxCommissionCents: null, suggestedCommissionBps: null };
const AFILIACAO = (status: "PENDING" | "APPROVED") => ({
  id: "af_1", productId: ITEM.productId, productName: ITEM.product, sellerId: "s", sellerName: "NT", affiliateId: "a", affiliateName: "Eu",
  commissionBps: status === "APPROVED" ? 5000 : 0, recurrence: "FIRST_CHARGE", status, endedReason: null, approvedAt: null, endedAt: null, createdAt: "2026-09-26T12:00:00Z",
});

async function preparar(page: import("@playwright/test").Page, afiliacoes: unknown[] = []) {
  await mockSessao(page, { ...SESSAO_VENDEDOR, activeMode: "AFFILIATE" });
  await mockDashboardVazio(page);
  await page.route("**/api/v1/marketplace**", (route) => route.fulfill({ json: { items: [ITEM, OUTRO], nextCursor: null } }));
  await page.route("**/api/v1/affiliations?**", (route) => route.fulfill({ json: { items: afiliacoes, nextCursor: null } }));
}

test.describe("marketplace de afiliados", () => {
  test("lista os produtos com o quanto o afiliado recebe e filtra pela busca", async ({ page }) => {
    await preparar(page);
    await page.goto("/vitrine");
    await expect(page.getByRole("heading", { name: "Marketplace" })).toBeVisible();
    await expect(page.getByText("R$ 48,50")).toBeVisible();
    await expect(page.getByText("Definida pelo vendedor")).toBeVisible();
    await page.getByPlaceholder("Buscar produto ou vendedor").fill("robô");
    await expect(page.getByRole("button", { name: /Combo Milionário/ })).toHaveCount(0);
    await expect(page.getByRole("button", { name: /Robô Studio/ })).toBeVisible();
  });

  test("abre o painel lateral, exige aceitar as condições e mostra a afiliação aprovada", async ({ page }) => {
    await preparar(page);
    await page.route("**/api/v1/affiliations", (route) => route.fulfill({ status: 201, json: AFILIACAO("APPROVED") }));
    await page.goto("/vitrine");
    await page.getByRole("button", { name: /Combo Milionário/ }).click();
    const botao = page.getByRole("button", { name: "Solicitar afiliação" });
    await expect(botao).toBeDisabled();
    await page.getByRole("tab", { name: "Detalhes" }).click();
    await expect(page.getByText("Aprenda a vender todos os dias.")).toBeVisible();
    await page.getByLabel("Li e entendi as condições").check();
    await botao.click();
    await expect(page.getByText(/Afiliação aprovada!/)).toBeVisible();
  });

  test("produto que já tem afiliação mostra o selo no card e no painel", async ({ page }) => {
    await preparar(page, [AFILIACAO("APPROVED")]);
    await page.goto("/vitrine");
    await page.getByRole("button", { name: /Combo Milionário/ }).click();
    await expect(page.getByText("Você já é um afiliado")).toBeVisible();
    await expect(page.getByRole("button", { name: "Solicitar afiliação" })).toHaveCount(0);
  });

  test("passa no axe com o painel aberto", async ({ page }) => {
    await preparar(page);
    await page.goto("/vitrine");
    await page.getByRole("button", { name: /Combo Milionário/ }).click();
    await expect(page.getByRole("tab", { name: "Produto" })).toBeVisible();
    const axe = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa"]).analyze();
    expect(axe.violations.filter(v => v.impact === "critical" || v.impact === "serious")).toEqual([]);
  });
});
