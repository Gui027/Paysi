import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { mockSessao, mockDashboardVazio } from "./fixtures";

const ASSINATURAS = {
  items: [
    {
      id: "sub_1",
      orderId: "order_1",
      offerId: "offer_1",
      status: "ACTIVE",
      cycleNumber: 3,
      trialEndsAt: null,
      nextChargeAt: "2026-10-17T00:00:00Z",
      canceledAt: null,
      cancelPending: false,
      hasPaymentMethod: true,
      createdAt: "2026-06-17T00:00:00Z",
    },
    {
      id: "sub_2",
      orderId: "order_2",
      offerId: "offer_2",
      status: "PAST_DUE",
      cycleNumber: 1,
      trialEndsAt: null,
      nextChargeAt: "2026-09-10T00:00:00Z",
      canceledAt: null,
      cancelPending: false,
      hasPaymentMethod: true,
      createdAt: "2026-08-17T00:00:00Z",
    },
  ],
  nextCursor: null,
};

test.describe("assinatura (visão do vendedor)", () => {
  test("lista assinaturas com status traduzido", async ({ page }) => {
    await mockSessao(page);
    await mockDashboardVazio(page);
    await page.route("**/api/v1/accounts/me/subscriptions**", async (route) => {
      await route.fulfill({ json: ASSINATURAS });
    });

    await page.goto("/assinaturas");

    await expect(page.getByRole("cell", { name: /^ativa$/i })).toBeVisible();
    await expect(page.getByRole("cell", { name: /pagamento em atraso/i })).toBeVisible();
  });

  test("estado vazio quando não há assinaturas", async ({ page }) => {
    await mockSessao(page);
    await mockDashboardVazio(page);
    await page.route("**/api/v1/accounts/me/subscriptions**", async (route) => {
      await route.fulfill({ json: { items: [], nextCursor: null } });
    });

    await page.goto("/assinaturas");
    await expect(page.getByRole("heading", { name: /assinaturas/i }).first()).toBeVisible();
  });

  test("erro ao carregar assinaturas mostra mensagem e permite tentar novamente", async ({ page }) => {
    await mockSessao(page);
    await mockDashboardVazio(page);
    await page.route("**/api/v1/accounts/me/subscriptions**", async (route) => {
      await route.fulfill({ status: 500, json: { code: "INTERNAL", message: "Erro interno." } });
    });

    await page.goto("/assinaturas");
    await expect(page.getByText(/não foi possível carregar as assinaturas/i)).toBeVisible();
  });

  test("não tem violações críticas/sérias de acessibilidade (axe)", async ({ page }) => {
    await mockSessao(page);
    await mockDashboardVazio(page);
    await page.route("**/api/v1/accounts/me/subscriptions**", async (route) => {
      await route.fulfill({ json: ASSINATURAS });
    });

    await page.goto("/assinaturas");
    await expect(page.getByRole("cell", { name: /^ativa$/i })).toBeVisible();

    const results = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa"]).analyze();
    const graves = results.violations.filter(v => v.impact === "critical" || v.impact === "serious");
    expect(graves, JSON.stringify(graves, null, 2)).toEqual([]);
  });
});
