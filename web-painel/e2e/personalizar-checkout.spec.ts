import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { mockSessao, mockDashboardVazio } from "./fixtures";

const OFERTA = { id: "off_ck_1", productId: "prod_ck_1", slug: "curso", priceCents: 9700, cycle: null, status: "DRAFT", immutableFields: [] };
const PRODUTO = { id: "prod_ck_1", name: "Curso de Vendas", description: null, segment: "DIGITAL", chargeType: "ONE_TIME", affiliationEnabled: false, status: "DRAFT", createdAt: "2026-09-25T12:00:00Z" };
const APARENCIA = { logoAssetId: null, bannerAssetId: null, sideImageAssetId: null, primaryColor: "#2563EB", buttonText: "Comprar agora", updatedAt: "2026-09-25T12:00:00Z" };

test.describe("personalizar checkout", () => {
  test.beforeEach(async ({ page }) => {
    await mockSessao(page);
    await mockDashboardVazio(page);
    await page.route(`**/api/v1/offers/${OFERTA.id}`, (route) => route.fulfill({ json: OFERTA }));
    await page.route(`**/api/v1/products/${PRODUTO.id}`, (route) => route.fulfill({ json: PRODUTO }));
  });

  test("prévia reflete o texto do botão e salva a aparência", async ({ page }) => {
    let enviado: Record<string, unknown> | null = null;
    await page.route(`**/api/v1/offers/${OFERTA.id}/appearance`, async (route) => {
      if (route.request().method() === "PUT") { enviado = route.request().postDataJSON(); await route.fulfill({ json: { ...APARENCIA, ...enviado } }); }
      else await route.fulfill({ json: APARENCIA });
    });
    await page.goto(`/aparencia/${OFERTA.id}`);
    await expect(page.getByText("Curso de Vendas").first()).toBeVisible();
    await page.getByLabel("Texto do botão").fill("Quero o curso");
    await expect(page.getByRole("button", { name: "Quero o curso", exact: true }).first()).toBeVisible();
    await expect(page.getByText("Existem alterações não salvas")).toBeVisible();
    await page.getByRole("button", { name: "Salvar checkout" }).click();
    await expect(page.getByText("Tudo certo!")).toBeVisible();
    expect(enviado).toMatchObject({ buttonText: "Quero o curso" });
  });

  test("alterna entre desktop e celular e passa no axe", async ({ page }) => {
    await page.route(`**/api/v1/offers/${OFERTA.id}/appearance`, (route) => route.fulfill({ json: APARENCIA }));
    await page.goto(`/aparencia/${OFERTA.id}`);
    await page.getByRole("button", { name: "Celular" }).click();
    await expect(page.getByRole("button", { name: "Celular" })).toHaveAttribute("aria-pressed", "true");
    const axe = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa"]).analyze();
    expect(axe.violations.filter(v => v.impact === "critical" || v.impact === "serious")).toEqual([]);
  });
});
