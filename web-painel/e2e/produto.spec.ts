import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { mockSessao, mockDashboardVazio } from "./fixtures";

const PRODUTO_CRIADO = {
  id: "prod_e2e_1",
  name: "Curso de Marketing",
  description: null,
  segment: "DIGITAL",
  chargeType: "ONE_TIME",
  affiliationEnabled: false,
  status: "DRAFT",
  createdAt: "2026-09-17T12:00:00Z",
};

async function mockShellEMenuBasico(page: import("@playwright/test").Page) {
  await mockSessao(page);
  await mockDashboardVazio(page);
}

test.describe("produto (criar produto)", () => {
  test("cria produto com sucesso e navega para o detalhe", async ({ page }) => {
    await mockShellEMenuBasico(page);
    await page.route("**/api/v1/products", async (route) => {
      if (route.request().method() === "POST") {
        await route.fulfill({ json: PRODUTO_CRIADO });
      } else {
        await route.continue();
      }
    });
    await page.route(`**/api/v1/products/${PRODUTO_CRIADO.id}`, async (route) => {
      await route.fulfill({ json: PRODUTO_CRIADO });
    });
    await page.route(`**/api/v1/products/${PRODUTO_CRIADO.id}/offers`, async (route) => {
      await route.fulfill({ json: [] });
    });

    await page.goto("/produtos/novo");
    await page.getByLabel(/nome do produto/i).fill("Curso de Marketing");
    await page.getByRole("button", { name: /salvar rascunho/i }).click();

    await expect(page).toHaveURL(new RegExp(`/produtos/${PRODUTO_CRIADO.id}$`));
    await expect(page.getByRole("heading", { name: PRODUTO_CRIADO.name })).toBeVisible();
    await expect(page.getByText(/nenhuma oferta disponível/i)).toBeVisible();
  });

  test("bloqueia envio sem nome do produto", async ({ page }) => {
    await mockShellEMenuBasico(page);
    await page.goto("/produtos/novo");
    await page.getByRole("button", { name: /salvar rascunho/i }).click();
    await expect(page.getByText(/informe o nome do produto/i)).toBeVisible();
  });

  test("erro de contrato imutável trava segmento e tipo de cobrança", async ({ page }) => {
    await mockShellEMenuBasico(page);
    await page.route("**/api/v1/products", async (route) => {
      if (route.request().method() === "POST") {
        await route.fulfill({
          status: 409,
          json: { code: "PRODUCT_CONTRACT_IMMUTABLE", message: "Este produto já possui oferta." },
        });
      } else {
        await route.continue();
      }
    });

    await page.goto("/produtos/novo");
    await page.getByLabel(/nome do produto/i).fill("Curso de Marketing");
    await page.getByRole("button", { name: /salvar rascunho/i }).click();

    await expect(page.getByText(/segmento e cobrança foram restaurados/i)).toBeVisible();
  });

  test("não tem violações críticas/sérias de acessibilidade (axe)", async ({ page }) => {
    await mockShellEMenuBasico(page);
    await page.goto("/produtos/novo");
    await expect(page.getByRole("heading", { name: /novo produto/i })).toBeVisible();

    const results = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa"]).analyze();
    const graves = results.violations.filter(v => v.impact === "critical" || v.impact === "serious");
    expect(graves, JSON.stringify(graves, null, 2)).toEqual([]);
  });
});
