import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { mockDashboardVazio } from "./fixtures";

test.describe("cadastro (criar conta)", () => {
  test("cria conta com sucesso e navega para o início", async ({ page }) => {
    let loginPayload: Record<string, string> | null = null;
    await page.route("**/api/v1/accounts", async (route) => {
      await route.fulfill({ json: { accountId: "acc_new_1", activeMode: "SELLER" } });
    });
    await page.route("**/api/v1/sessions", async (route) => {
      loginPayload = route.request().postDataJSON() as Record<string, string>;
      await route.fulfill({ json: { accountId: "acc_new_1", activeMode: "SELLER", expiresAt: "2026-09-18T00:00:00Z" } });
    });
    // O redirecionamento pós-cadastro chama SessionGuard -> currentSession.
    await page.route("**/api/v1/sessions/current", async (route) => {
      await route.fulfill({ json: { accountId: "acc_new_1", activeMode: "SELLER", expiresAt: "2026-09-18T00:00:00Z" } });
    });
    await mockDashboardVazio(page);

    await page.goto("/criar-conta");

    await page.getByLabel(/nome completo ou razão social/i).fill("Maria Vendedora");
    await page.getByLabel(/e-mail/i).fill("maria.vendedora@example.com");
    await page.getByLabel("CPF").fill("39053344705");
    await page.getByLabel("Senha", { exact: true }).fill("SenhaForte123");
    await page.getByLabel(/confirme a senha/i).fill("SenhaForte123");
    await page.getByRole("checkbox", { name: /li e aceito os termos/i }).check();
    await page.getByRole("button", { name: /criar conta/i }).click();

    await expect(page).toHaveURL(/\/inicio/);
    expect(loginPayload).toEqual({
      email: "maria.vendedora@example.com",
      password: "SenhaForte123",
      initialMode: "SELLER",
    });
  });

  test("bloqueia envio com senhas diferentes e sem aceitar os termos", async ({ page }) => {
    await page.goto("/criar-conta");

    await page.getByLabel(/nome completo ou razão social/i).fill("Maria Vendedora");
    await page.getByLabel(/e-mail/i).fill("maria.vendedora@example.com");
    await page.getByLabel("CPF").fill("39053344705");
    await page.getByLabel("Senha", { exact: true }).fill("SenhaForte123");
    await page.getByLabel(/confirme a senha/i).fill("OutraSenha456");
    await page.getByRole("button", { name: /criar conta/i }).click();

    await expect(page.getByText(/as senhas precisam ser iguais/i)).toBeVisible();
    await expect(page.getByText(/você precisa aceitar os termos/i)).toBeVisible();
  });

  test("erro de validação do backend é exibido no campo certo", async ({ page }) => {
    await page.route("**/api/v1/accounts", async (route) => {
      await route.fulfill({
        status: 422,
        json: { field: "email", code: "EMAIL_TAKEN", message: "Este e-mail já está cadastrado." },
      });
    });

    await page.goto("/criar-conta");
    await page.getByLabel(/nome completo ou razão social/i).fill("Maria Vendedora");
    await page.getByLabel(/e-mail/i).fill("existente@example.com");
    await page.getByLabel("CPF").fill("39053344705");
    await page.getByLabel("Senha", { exact: true }).fill("SenhaForte123");
    await page.getByLabel(/confirme a senha/i).fill("SenhaForte123");
    await page.getByRole("checkbox", { name: /li e aceito os termos/i }).check();
    await page.getByRole("button", { name: /criar conta/i }).click();

    await expect(page.getByText(/este e-mail já está cadastrado/i)).toBeVisible();
  });

  test("não tem violações críticas/sérias de acessibilidade (axe)", async ({ page }) => {
    await page.goto("/criar-conta");
    const results = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa"]).analyze();
    const graves = results.violations.filter(v => v.impact === "critical" || v.impact === "serious");
    expect(graves, JSON.stringify(graves, null, 2)).toEqual([]);
  });

  test("é possível preencher e enviar o formulário só com teclado", async ({ page }) => {
    await page.route("**/api/v1/accounts", async (route) => {
      await route.fulfill({ json: { accountId: "acc_new_2", activeMode: "SELLER" } });
    });
    await page.route("**/api/v1/sessions", async (route) => {
      await route.fulfill({ json: { accountId: "acc_new_2", activeMode: "SELLER", expiresAt: "2026-09-18T00:00:00Z" } });
    });
    await page.route("**/api/v1/sessions/current", async (route) => {
      await route.fulfill({ json: { accountId: "acc_new_2", activeMode: "SELLER", expiresAt: "2026-09-18T00:00:00Z" } });
    });
    await mockDashboardVazio(page);

    await page.goto("/criar-conta");
    await page.getByLabel(/nome completo ou razão social/i).click();
    await page.keyboard.type("Maria Vendedora");
    await page.keyboard.press("Tab");
    await page.keyboard.type("maria.vendedora@example.com");

    await page.getByLabel("CPF").fill("39053344705");
    await page.getByLabel("Senha", { exact: true }).fill("SenhaForte123");
    await page.getByLabel(/confirme a senha/i).fill("SenhaForte123");

    await page.getByRole("checkbox", { name: /li e aceito os termos/i }).focus();
    await page.keyboard.press("Space");

    await page.getByRole("button", { name: /criar conta/i }).focus();
    await page.keyboard.press("Enter");

    await expect(page).toHaveURL(/\/inicio/);
  });
});
