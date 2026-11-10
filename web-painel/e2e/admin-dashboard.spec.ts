import { test, expect, Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

const contas = [
  { id: "acc-1", name: "Conta Alpha", email: "alpha@empresa.com", status: "ACTIVE", revenueCents: 985000, paidSales: 42, createdAt: "2026-08-10T12:00:00Z" },
  { id: "acc-2", name: "Studio Beta", email: "beta@empresa.com", status: "LIMITED", revenueCents: 125000, paidSales: 8, createdAt: "2026-10-02T12:00:00Z" },
];

function dashboard(days = 30) {
  return {
    summary: { totalRevenueCents: 1_250_000, periodRevenueCents: 320_000, paidSales: 50, activeSubscriptions: 18, newAccounts: 7 },
    revenueTimeline: Array.from({ length: days }, (_, index) => ({
      date: new Date(Date.UTC(2026, 10, 10 - (days - 1 - index))).toISOString().slice(0, 10),
      revenueCents: index % 4 === 0 ? 35000 : 8000 + index * 500,
      sales: index % 4 === 0 ? 4 : 1,
    })),
    accounts: contas, page: 0, size: 20, totalAccounts: 2, totalPages: 1,
  };
}

async function mockAdmin(page: Page) {
  const requests: URL[] = [];
  await page.route("**/api/v1/admin-dashboard**", async route => {
    const request = route.request();
    const url = new URL(request.url());
    requests.push(url);
    if (url.pathname.endsWith("/sessions") && request.method() === "POST") {
      return route.fulfill({ json: { username: "admin" } });
    }
    if (url.pathname.endsWith("/sessions/current") && request.method() === "DELETE") {
      return route.fulfill({ status: 204 });
    }
    const days = Number(url.searchParams.get("days") ?? 30);
    return route.fulfill({ json: dashboard(days) });
  });
  return requests;
}

test.describe("dashboard administrativa", () => {
  test("login administrativo valida credenciais e abre a dashboard", async ({ page }) => {
    await mockAdmin(page);
    await page.goto("/entrar");
    await expect(page.getByRole("heading", { name: "Entre na sua conta" })).toBeVisible();
    await page.getByLabel("E-mail ou usuário").fill("admin");
    await page.getByLabel("Senha").fill("Siad3797");
    await page.getByRole("button", { name: "Entrar" }).click();
    await expect(page).toHaveURL(/\/admin$/);
    await expect(page.getByRole("heading", { name: "Dashboard administrativo" })).toBeVisible();
  });

  test("mostra indicadores, gráfico, contas e filtros com acessibilidade", async ({ page }) => {
    const requests = await mockAdmin(page);
    await page.goto("/admin");
    await expect(page.getByText("R$ 12.500,00")).toBeVisible();
    await expect(page.getByRole("img", { name: /Gráfico do faturamento diário/ })).toBeVisible();
    await expect(page.getByRole("cell", { name: /Conta Alpha/ })).toContainText("alpha@empresa.com");
    await expect(page.getByRole("columnheader", { name: "Criada em" })).toBeVisible();
    await page.getByRole("button", { name: "7 dias" }).click();
    await expect.poll(() => requests.at(-1)?.searchParams.get("days")).toBe("7");
    await page.getByRole("searchbox", { name: "Buscar conta" }).fill("alpha");
    await expect.poll(() => requests.at(-1)?.searchParams.get("q")).toBe("alpha");
    expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  });

  test("sessão inválida redireciona para o login e sair encerra a sessão", async ({ page }) => {
    await page.route("**/api/v1/admin-dashboard**", route => {
      if (route.request().method() === "DELETE") return route.fulfill({ status: 204 });
      return route.fulfill({ status: 401, json: { code: "ADMIN_SESSION_INVALID" } });
    });
    await page.goto("/admin");
    await expect(page).toHaveURL(/\/entrar$/);

    await page.unroute("**/api/v1/admin-dashboard**");
    await mockAdmin(page);
    await page.goto("/admin");
    await page.getByRole("button", { name: "Sair" }).click();
    await expect(page).toHaveURL(/\/entrar$/);
  });

  test("rota antiga de login administrativo usa a página comum", async ({ page }) => {
    await page.goto("/admin/entrar");
    await expect(page).toHaveURL(/\/entrar$/);
    await expect(page.getByRole("heading", { name: "Entre na sua conta" })).toBeVisible();
  });
});
