import { test, expect } from "@playwright/test";
import { mockSessao } from "./fixtures";

test.describe("dashboard do vendedor", () => {
  test("o alerta de verificação de identidade pendente tem um botão que leva à aba Identidade", async ({ page }) => {
    await mockSessao(page);
    await page.route("**/api/v1/accounts/me/dashboard**", (route) => route.fulfill({
      json: {
        period: { preset: "today", from: "2026-09-17", to: "2026-09-17" },
        salesToday: { state: "EMPTY" },
        balance: { state: "EMPTY" },
        nextReceivables: { state: "EMPTY" },
        subscriptions: { state: "EMPTY" },
        recentSales: { state: "EMPTY" },
        alerts: { state: "SUCCESS", data: [{ id: "kyc", tone: "warning", title: "Verificação de identidade pendente", description: "Inicie a verificação para poder publicar ofertas e receber pagamentos.", actionUrl: "/saldo?aba=identidade" }] },
      },
    }));
    await page.goto("/inicio");
    await expect(page.getByText("Verificação de identidade pendente")).toBeVisible();
    const botao = page.getByRole("link", { name: "Continuar" });
    await expect(botao).toBeVisible();
    await expect(botao).toHaveAttribute("href", "/saldo?aba=identidade");
  });
});
