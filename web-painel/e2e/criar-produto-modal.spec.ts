import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { mockSessao, mockDashboardVazio } from "./fixtures";

const PRODUTO = { id: "prod_modal_1", name: "Curso de Vendas", description: null, segment: "DIGITAL", chargeType: "ONE_TIME", affiliationEnabled: false, status: "DRAFT", createdAt: "2026-09-25T12:00:00Z" };

test.describe("criar produto (modal em dois passos)", () => {
  test("cria produto e oferta padrão e abre o detalhe", async ({ page }) => {
    await mockSessao(page);
    await mockDashboardVazio(page);
    let ofertaEnviada: Record<string, unknown> | null = null;
    await page.route("**/api/v1/products", async (route) => {
      if (route.request().method() === "POST") await route.fulfill({ json: PRODUTO });
      else await route.fulfill({ json: { items: [], nextCursor: null } });
    });
    await page.route(`**/api/v1/products/${PRODUTO.id}/offers`, async (route) => {
      if (route.request().method() === "POST") { ofertaEnviada = route.request().postDataJSON(); await route.fulfill({ json: { id: "off_1" } }); }
      else await route.fulfill({ json: [] });
    });
    await page.route(`**/api/v1/products/${PRODUTO.id}`, (route) => route.fulfill({ json: PRODUTO }));

    await page.goto("/produtos");
    await page.getByRole("button", { name: "Criar produto" }).first().click();
    await page.getByRole("button", { name: /continuar/i }).click();
    await page.getByLabel("Nome do produto").fill("Curso de Vendas");
    await page.getByLabel("Preço em reais").fill("97,00");
    await page.getByRole("dialog").getByRole("button", { name: "Criar produto" }).click();

    await expect(page).toHaveURL(new RegExp(`/produtos/${PRODUTO.id}$`));
    expect(ofertaEnviada).toMatchObject({ priceCents: 9700, cycle: null, guaranteeDays: 7 });
  });

  test("valida nome e preço mínimo no segundo passo e passa no axe", async ({ page }) => {
    await mockSessao(page);
    await mockDashboardVazio(page);
    await page.route("**/api/v1/products", (route) => route.fulfill({ json: { items: [], nextCursor: null } }));
    await page.goto("/produtos");
    await page.getByRole("button", { name: "Criar produto" }).first().click();
    const axe = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa"]).analyze();
    expect(axe.violations.filter(v => v.impact === "critical" || v.impact === "serious")).toEqual([]);
    await page.getByRole("button", { name: /continuar/i }).click();
    await page.getByLabel("Preço em reais").fill("5");
    await page.getByRole("dialog").getByRole("button", { name: "Criar produto" }).click();
    await expect(page.getByText(/informe o nome do produto/i)).toBeVisible();
    await expect(page.getByText(/preço mínimo de R\$ 20,00/i)).toBeVisible();
  });
});
