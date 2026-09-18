import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { mockSessao } from "./fixtures";

const LINKS = [
  { affiliationId: "aff_1", productId: "prod_1", productName: "Curso de Vendas", offerSlug: "curso-vendas", clicks: 42, orders: 3 },
  { affiliationId: "aff_2", productId: "prod_2", productName: "Ebook de Marketing", offerSlug: null, clicks: 5, orders: 0 },
];

const LEDGER_VAZIO = { items: [], nextCursor: null };

test.describe("afiliação (meus links)", () => {
  test("lista os links de afiliado e permite copiar o link disponível", async ({ page }) => {
    await mockSessao(page);
    await page.route("**/api/v1/affiliations/links", async (route) => {
      await route.fulfill({ json: LINKS });
    });
    await page.route("**/api/v1/accounts/me/ledger**", async (route) => {
      await route.fulfill({ json: LEDGER_VAZIO });
    });
    await page.context().grantPermissions(["clipboard-read", "clipboard-write"]);

    await page.goto("/meus-links");

    await expect(page.getByText("Curso de Vendas")).toBeVisible();
    await expect(page.getByText("Ebook de Marketing")).toBeVisible();
    await expect(page.getByText(/sem oferta publicada/i)).toBeVisible();

    await page.getByRole("button", { name: /copiar link/i }).click();
    await expect(page.getByRole("button", { name: /link copiado/i })).toBeVisible();
  });

  test("estado vazio quando não há links de afiliado", async ({ page }) => {
    await mockSessao(page);
    await page.route("**/api/v1/affiliations/links", async (route) => {
      await route.fulfill({ json: [] });
    });
    await page.route("**/api/v1/accounts/me/ledger**", async (route) => {
      await route.fulfill({ json: LEDGER_VAZIO });
    });

    await page.goto("/meus-links");
    await expect(page.getByRole("heading", { name: /meus links e comissões/i })).toBeVisible();
  });

  test("erro ao carregar links mostra mensagem amigável", async ({ page }) => {
    await mockSessao(page);
    await page.route("**/api/v1/affiliations/links", async (route) => {
      await route.fulfill({ status: 500, json: { code: "INTERNAL", message: "Erro interno." } });
    });
    await page.route("**/api/v1/accounts/me/ledger**", async (route) => {
      await route.fulfill({ json: LEDGER_VAZIO });
    });

    await page.goto("/meus-links");
    await expect(page.getByText(/não foi possível carregar seus links/i)).toBeVisible();
  });

  test("não tem violações críticas/sérias de acessibilidade (axe)", async ({ page }) => {
    await mockSessao(page);
    await page.route("**/api/v1/affiliations/links", async (route) => {
      await route.fulfill({ json: LINKS });
    });
    await page.route("**/api/v1/accounts/me/ledger**", async (route) => {
      await route.fulfill({ json: LEDGER_VAZIO });
    });

    await page.goto("/meus-links");
    await expect(page.getByText("Curso de Vendas")).toBeVisible();

    const results = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa"]).analyze();
    const graves = results.violations.filter(v => v.impact === "critical" || v.impact === "serious");
    expect(graves, JSON.stringify(graves, null, 2)).toEqual([]);
  });
});
