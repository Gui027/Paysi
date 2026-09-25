import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { mockSessao, mockDashboardVazio } from "./fixtures";

const PRODUTO = { id: "prod_ed_1", name: "Curso de Vendas", description: "Aprenda a vender.", segment: "DIGITAL", chargeType: "ONE_TIME", affiliationEnabled: false, status: "DRAFT", createdAt: "2026-09-25T12:00:00Z" };
const OFERTA = { id: "off_ed_1", productId: PRODUTO.id, chargeType: "ONE_TIME", segment: "DIGITAL", slug: "curso-vendas", priceCents: 9700, cycle: null, trialDays: 0, trialRequiresCard: true, guaranteeDays: 7, maxInstallments: 1, boletoDueDays: 3, boletoAdvanceDays: 5, paymentMethods: ["PIX", "CARD"], payoutDelay: "D32", status: "DRAFT", availableAt: "2026-09-25T12:00:00Z", immutableFields: [], createdAt: "2026-09-25T12:00:00Z", updatedAt: "2026-09-25T12:00:00Z" };

test.describe("editor do produto (abas)", () => {
  test.beforeEach(async ({ page }) => {
    await mockSessao(page);
    await mockDashboardVazio(page);
    await page.route(`**/api/v1/products/${PRODUTO.id}`, async (route) => {
      if (route.request().method() === "PUT") await route.fulfill({ json: { ...PRODUTO, ...route.request().postDataJSON() } });
      else await route.fulfill({ json: PRODUTO });
    });
    await page.route(`**/api/v1/products/${PRODUTO.id}/offers`, (route) => route.fulfill({ json: [OFERTA] }));
  });

  test("salva nome, preço e parcelamento em uma só ação", async ({ page }) => {
    let oferta: Record<string, unknown> | null = null;
    await page.route(`**/api/v1/offers/${OFERTA.id}`, async (route) => { oferta = route.request().postDataJSON(); await route.fulfill({ json: { ...OFERTA, ...oferta } }); });
    await page.goto(`/produtos/${PRODUTO.id}`);
    await expect(page.getByLabel("Preço em reais")).toHaveValue("97,00");
    await page.getByLabel("Preço em reais").fill("147,50");
    await page.getByRole("tab", { name: "Configurações" }).click();
    await page.getByLabel("Parcelamento").selectOption("6");
    await page.getByRole("button", { name: "Salvar produto" }).first().click();
    await expect(page.getByText("Produto salvo.")).toBeVisible();
    expect(oferta).toMatchObject({ priceCents: 14750, maxInstallments: 6 });
  });

  test("publica o checkout e mostra o link", async ({ page }) => {
    await page.route(`**/api/v1/offers/${OFERTA.id}/publish`, (route) => route.fulfill({ json: { published: true, requiredAction: null, actionUrl: null, offer: { ...OFERTA, status: "PUBLISHED" } } }));
    await page.goto(`/produtos/${PRODUTO.id}?aba=checkout`);
    await page.getByRole("button", { name: "Publicar checkout" }).click();
    await expect(page.getByText("Checkout publicado", { exact: true }).first()).toBeVisible();
    await expect(page.getByRole("link", { name: /checkout\/curso-vendas/ })).toBeVisible();
  });

  test("passa no axe em todas as abas", async ({ page }) => {
    for (const aba of ["", "?aba=configuracoes", "?aba=checkout", "?aba=afiliados"]) {
      await page.goto(`/produtos/${PRODUTO.id}${aba}`);
      await expect(page.getByRole("tab", { name: "Geral" })).toBeVisible();
      const axe = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa"]).analyze();
      expect(axe.violations.filter(v => v.impact === "critical" || v.impact === "serious"), aba).toEqual([]);
    }
  });
});
